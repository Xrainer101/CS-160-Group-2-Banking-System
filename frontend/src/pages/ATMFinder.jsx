import { useCallback, useEffect, useRef, useState } from "react";
import {
  MapContainer,
  TileLayer,
  Marker,
  Popup,
  CircleMarker,
  useMap,
} from "react-leaflet";
import L from "leaflet";
import {
  Search,
  MapPin,
  Navigation,
  LocateFixed,
  RefreshCw,
  ExternalLink,
  ArrowRight,
  Building2,
  Crosshair,
} from "lucide-react";

import "leaflet/dist/leaflet.css";

// --------------------------------------------------
// Configuration
// --------------------------------------------------

const DEFAULT_LOCATION = {
  lat: 37.3382,
  lon: -121.8863,
  label: "San Jose, California",
};

const RADIUS_METERS = 10000;

const OVERPASS_SERVERS = [
  "https://overpass.private.coffee/api/interpreter",
  "https://overpass-api.de/api/interpreter",
];

const CHASE_ID = "Q524629";

const COLORS = {
  blue: "#1459c7",
  dark: "#102c57",
  light: "#edf4ff",
  border: "#dfe7f2",
  muted: "#64748b",
};

// --------------------------------------------------
// Helpers
// --------------------------------------------------

function distanceMiles(a, b) {
  const rad = (n) => (n * Math.PI) / 180;

  const dLat = rad(b.lat - a.lat);
  const dLon = rad(b.lon - a.lon);

  const value =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(a.lat)) *
      Math.cos(rad(b.lat)) *
      Math.sin(dLon / 2) ** 2;

  return (
    3958.76 *
    2 *
    Math.atan2(
      Math.sqrt(value),
      Math.sqrt(1 - value)
    )
  );
}

function isChase(tags) {
  const values = [
    tags.name,
    tags.brand,
    tags.operator,
    tags.network,
    tags["brand:en"],
    tags["operator:en"],
  ];

  const matchesName = values.some(
    (value) =>
      typeof value === "string" &&
      /\bchase\b|jpmorgan chase/i.test(value)
  );

  const matchesWikidata = [
    tags["brand:wikidata"],
    tags["operator:wikidata"],
  ].some(
    (value) =>
      typeof value === "string" &&
      value.split(";").some(
        (item) => item.trim() === CHASE_ID
      )
  );

  return matchesName || matchesWikidata;
}

function formatAddress(tags) {
  const street = [
    tags["addr:housenumber"],
    tags["addr:street"],
  ].filter(Boolean).join(" ");

  const locality = [
    tags["addr:city"],
    tags["addr:state"],
    tags["addr:postcode"],
  ].filter(Boolean).join(", ");

  return (
    [street, locality].filter(Boolean).join(", ") ||
    tags["addr:full"] ||
    "Address not available"
  );
}

function makeQuery(lat, lon) {
  const around = `(around:${RADIUS_METERS},${lat},${lon})`;

  // Include standalone Chase ATMs and Chase
  // branches explicitly tagged as having an ATM.
  return `
    [out:json][timeout:25];
    (
      nwr${around}["amenity"="atm"]["name"~"Chase",i];
      nwr${around}["amenity"="atm"]["brand"~"Chase",i];
      nwr${around}["amenity"="atm"]["operator"~"Chase",i];
      nwr${around}["amenity"="atm"]["network"~"Chase",i];
      nwr${around}["amenity"="atm"]["brand:wikidata"="${CHASE_ID}"];
      nwr${around}["amenity"="atm"]["operator:wikidata"="${CHASE_ID}"];

      nwr${around}["amenity"="bank"]["atm"="yes"]["name"~"Chase",i];
      nwr${around}["amenity"="bank"]["atm"="yes"]["brand"~"Chase",i];
      nwr${around}["amenity"="bank"]["atm"="yes"]["operator"~"Chase",i];
      nwr${around}["amenity"="bank"]["atm"="yes"]["brand:wikidata"="${CHASE_ID}"];
      nwr${around}["amenity"="bank"]["atm"="yes"]["operator:wikidata"="${CHASE_ID}"];
    );
    out center;
  `;
}

function parseATMs(elements, location) {
  const found = [];

  for (const item of elements) {
    const tags = item.tags || {};

    if (!isChase(tags)) continue;

    const isATM = tags.amenity === "atm";
    const isBankATM =
      tags.amenity === "bank" &&
      tags.atm === "yes";

    if (!isATM && !isBankATM) continue;

    const lat = Number(
      item.lat ?? item.center?.lat
    );

    const lon = Number(
      item.lon ?? item.center?.lon
    );

    if (!Number.isFinite(lat) ||
        !Number.isFinite(lon)) continue;

    found.push({
      id: `${item.type}-${item.id}`,
      lat,
      lon,
      name: "Chase ATM",
      kind: isATM
        ? "ATM location"
        : "Chase branch with ATM",
      address: formatAddress(tags),
      hours:
        tags["opening_hours:atm"] ||
        tags.opening_hours ||
        "",
      miles: distanceMiles(location, { lat, lon }),
      standalone: isATM,
    });
  }

  // Prefer specific ATM nodes when an ATM and its
  // enclosing branch are mapped at the same place.
  found.sort(
    (a, b) =>
      Number(b.standalone) -
      Number(a.standalone)
  );

  const unique = [];

  for (const atm of found) {
    const duplicate = unique.some(
      (other) =>
        other.id === atm.id ||
        distanceMiles(other, atm) < 0.02
    );

    if (!duplicate) unique.push(atm);
  }

  return unique
    .sort((a, b) => a.miles - b.miles)
    .map((atm, index) => ({
      ...atm,
      number: index + 1,
    }));
}

async function requestJSON(
  url,
  options = {},
  timeout = 30000
) {
  const controller = new AbortController();
  const timer = setTimeout(
    () => controller.abort(),
    timeout
  );

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`);
    }

    return await response.json();
  } finally {
    clearTimeout(timer);
  }
}

async function loadATMs(location) {
  const query = makeQuery(location.lat, location.lon);
  let lastError;

  for (const endpoint of OVERPASS_SERVERS) {
    try {
      const data = await requestJSON(
        endpoint,
        {
          method: "POST",
          headers: {
            "Content-Type":
              "application/x-www-form-urlencoded",
          },
          body: new URLSearchParams({
            data: query,
          }).toString(),
        },
        30000
      );

      if (!Array.isArray(data.elements)) {
        throw new Error("Invalid location response");
      }

      return parseATMs(data.elements, location);
    } catch (error) {
      lastError = error;
    }
  }

  throw lastError || new Error(
    "Unable to load ATM data"
  );
}

function directionsURL(atm) {
  return (
    "https://www.openstreetmap.org/directions" +
    `?to=${encodeURIComponent(
      `${atm.lat},${atm.lon}`
    )}`
  );
}

// --------------------------------------------------
// Map markers
// --------------------------------------------------

function atmIcon(number, active) {
  const size = active ? 46 : 38;

  return L.divIcon({
    className: "chase-atm-custom-marker",
    html: `
      <div style="
        width:${size}px;
        height:${size}px;
        display:flex;
        align-items:center;
        justify-content:center;
        background:${active ? "#102c57" : "#1459c7"};
        border:3px solid white;
        border-radius:16px 16px 16px 4px;
        transform:rotate(-45deg);
        box-shadow:0 5px 18px rgba(10,36,80,.28);
      ">
        <span style="
          transform:rotate(45deg);
          color:white;
          font-size:15px;
          font-weight:800;
          font-family:Arial,sans-serif;
        ">${number}</span>
      </div>
    `,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
    popupAnchor: [0, -size / 2],
  });
}

function MapEvents({ onCenterChange }) {
  const map = useMap();

  useEffect(() => {
    function handleMove() {
      const point = map.getCenter();

      onCenterChange({
        lat: point.lat,
        lon: point.lng,
      });
    }

    map.on("moveend", handleMove);

    return () => {
      map.off("moveend", handleMove);
    };
  }, [map, onCenterChange]);

  return null;
}

function MapViewController({
  location,
  results,
  selected,
  markers,
}) {
  const map = useMap();

  useEffect(() => {
    if (!location) return;

    if (!results.length) {
      map.setView(
        [location.lat, location.lon],
        13
      );
      return;
    }

    const bounds = L.latLngBounds([
      [location.lat, location.lon],
      ...results.map(
        (atm) => [atm.lat, atm.lon]
      ),
    ]);

    map.fitBounds(bounds, {
      padding: [60, 60],
      maxZoom: 14,
    });
  }, [map, location, results]);

  useEffect(() => {
    if (!selected) return;

    map.flyTo(
      [selected.lat, selected.lon],
      16,
      { duration: 0.6 }
    );

    const marker = markers.current[selected.id];

    if (marker) marker.openPopup();
  }, [map, selected, markers]);

  return null;
}

// --------------------------------------------------
// Main ATM Finder
// --------------------------------------------------

export default function ATMFinder() {
  const [location, setLocation] =
    useState(DEFAULT_LOCATION);

  const [mapCenter, setMapCenter] =
    useState(DEFAULT_LOCATION);

  const [search, setSearch] =
    useState("San Jose, CA");

  const [results, setResults] =
    useState([]);

  const [selected, setSelected] =
    useState(null);

  const [loading, setLoading] =
    useState(false);

  const [error, setError] =
    useState("");

  const [hasSearched, setHasSearched] =
    useState(false);

  const markerRefs = useRef({});
  const requestRef = useRef(0);
  const mapSectionRef = useRef(null);
  const mountedRef = useRef(true);

  const updateMapCenter = useCallback(
    (point) => setMapCenter(point),
    []
  );

  const searchArea = useCallback(
    async (point) => {
      const request = ++requestRef.current;

      setLoading(true);
      setError("");
      setSelected(null);
      setHasSearched(true);
      setLocation(point);
      setResults([]);

      try {
        const found = await loadATMs(point);

        if (
          !mountedRef.current ||
          request !== requestRef.current
        ) return;

        setResults(found);
      } catch (failure) {
        if (
          !mountedRef.current ||
          request !== requestRef.current
        ) return;

        setError(
          "Could not load ATM locations right now. " +
          "The public OpenStreetMap data service " +
          "may be busy. Please try again."
        );
      } finally {
        if (
          mountedRef.current &&
          request === requestRef.current
        ) {
          setLoading(false);
        }
      }
    },
    []
  );

  useEffect(() => {
    mountedRef.current = true;
    searchArea(DEFAULT_LOCATION);

    return () => {
      mountedRef.current = false;
      requestRef.current += 1;
    };
  }, [searchArea]);

  async function handleSearch(event) {
    event.preventDefault();

    const query = search.trim();

    if (!query || loading) return;

    setLoading(true);
    setError("");

    try {
      const params = new URLSearchParams({
        q: query,
        format: "jsonv2",
        limit: "1",
      });

      const places = await requestJSON(
        "https://nominatim.openstreetmap.org/search?" +
        params.toString(),
        {
          headers: {
            Accept: "application/json",
          },
        },
        15000
      );

      if (!places.length) {
        throw new Error(
          "Location not found. Try a city or ZIP code."
        );
      }

      const point = {
        lat: Number(places[0].lat),
        lon: Number(places[0].lon),
        label: places[0].display_name,
      };

      await searchArea(point);
    } catch (failure) {
      setError(
        failure.message ||
        "Unable to find that location."
      );
    } finally {
      if (mountedRef.current) setLoading(false);
    }
  }

  function handleNearMe() {
    if (!navigator.geolocation) {
      setError(
        "Your browser does not support location access."
      );
      return;
    }

    setLoading(true);
    setError("");

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const point = {
          lat: position.coords.latitude,
          lon: position.coords.longitude,
          label: "My current location",
        };

        setSearch("My current location");
        searchArea(point);
      },
      (failure) => {
        setLoading(false);
        setError(
          "Could not access your location: " +
          failure.message
        );
      },
      {
        enableHighAccuracy: false,
        timeout: 12000,
      }
    );
  }

  function selectATM(atm) {
    setSelected(atm);

    mapSectionRef.current?.scrollIntoView({
      behavior: "smooth",
      block: "center",
    });
  }

  const nearest = results[0];

  return (
    <div style={{ paddingBottom: 30 }}>
      {/* Header */}

      <div className="page-heading">
        <span className="eyebrow">
          LOCATIONS & SERVICES
        </span>

        <h1>Find Chase ATMs</h1>

        <p>
          Find nearby Chase ATM locations,
          explore the map, and get directions.
        </p>
      </div>

      {/* Search toolbar */}

      <section
        className="panel"
        style={{
          borderRadius: 18,
          marginBottom: 20,
        }}
      >
        <div
          style={{
            display: "flex",
            flexWrap: "wrap",
            gap: 12,
            alignItems: "end",
          }}
        >
          <form
            onSubmit={handleSearch}
            style={{
              display: "flex",
              flex: "1 1 300px",
              gap: 10,
              flexWrap: "wrap",
              alignItems: "end",
            }}
          >
            <label
              style={{
                flex: "1 1 200px",
              }}
            >
              Search location

              <div
                style={{
                  position: "relative",
                  marginTop: 6,
                }}
              >
                <MapPin
                  size={17}
                  color={COLORS.muted}
                  style={{
                    position: "absolute",
                    left: 12,
                    top: 13,
                  }}
                />

                <input
                  value={search}
                  onChange={(event) =>
                    setSearch(event.target.value)
                  }
                  placeholder="City, address, or ZIP"
                  style={{
                    width: "100%",
                    padding: "11px 12px 11px 38px",
                  }}
                />
              </div>
            </label>

            <button
              type="submit"
              className="button button-primary"
              disabled={loading}
              style={{ minHeight: 43 }}
            >
              <Search size={17} />
              Search
            </button>
          </form>

          <button
            type="button"
            className="button button-outline"
            onClick={handleNearMe}
            disabled={loading}
            style={{ minHeight: 43 }}
          >
            <LocateFixed size={17} />
            Near Me
          </button>
        </div>

        {error && (
          <p
            role="alert"
            className="form-error"
            style={{ marginTop: 16 }}
          >
            {error}
          </p>
        )}

        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            flexWrap: "wrap",
            gap: 12,
            marginTop: 18,
          }}
        >
          <span
            style={{
              fontSize: 13,
              color: COLORS.muted,
            }}
          >
            <Crosshair
              size={15}
              style={{
                display: "inline",
                verticalAlign: "middle",
                marginRight: 5,
              }}
            />
            {location.label}
          </span>

          <span
            style={{
              background: COLORS.light,
              color: COLORS.blue,
              padding: "5px 12px",
              borderRadius: 20,
              fontWeight: 700,
              fontSize: 12,
            }}
          >
            {loading
              ? "Searching..."
              : `${results.length} Chase ATM locations`}
          </span>
        </div>
      </section>

      {/* Map */}

      <section
        className="panel"
        ref={mapSectionRef}
        style={{
          borderRadius: 18,
          overflow: "hidden",
          padding: 0,
        }}
      >
        <div
          style={{
            padding: "20px 22px",
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            flexWrap: "wrap",
            gap: 12,
          }}
        >
          <div>
            <h2 style={{ margin: "0 0 5px" }}>
              Nearby ATM Map
            </h2>

            <span
              style={{
                fontSize: 13,
                color: COLORS.muted,
              }}
            >
              Drag, zoom, and select markers.
            </span>
          </div>

          <button
            type="button"
            className="button button-outline"
            disabled={loading}
            onClick={() =>
              searchArea({
                ...mapCenter,
                label: "Selected map area",
              })
            }
          >
            <RefreshCw size={16} />
            Search This Map Area
          </button>
        </div>

        <div
          style={{
            position: "relative",
            height: 530,
            width: "100%",
            background: "#eaf0f7",
          }}
        >
          <MapContainer
            center={[
              DEFAULT_LOCATION.lat,
              DEFAULT_LOCATION.lon,
            ]}
            zoom={13}
            minZoom={4}
            maxZoom={19}
            scrollWheelZoom={true}
            zoomControl={true}
            style={{
              height: "100%",
              width: "100%",
            }}
          >
            <TileLayer
              url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'
              maxZoom={19}
            />

            <MapEvents
              onCenterChange={updateMapCenter}
            />

            <MapViewController
              location={location}
              results={results}
              selected={selected}
              markers={markerRefs}
            />

            <CircleMarker
              center={[location.lat, location.lon]}
              radius={7}
              pathOptions={{
                color: "white",
                weight: 3,
                fillColor: "#11a990",
                fillOpacity: 1,
              }}
            >
              <Popup>Search center</Popup>
            </CircleMarker>

            {results.map((atm) => (
              <Marker
                key={atm.id}
                position={[atm.lat, atm.lon]}
                icon={atmIcon(
                  atm.number,
                  selected?.id === atm.id
                )}
                ref={(marker) => {
                  if (marker) {
                    markerRefs.current[atm.id] = marker;
                  } else {
                    delete markerRefs.current[atm.id];
                  }
                }}
                eventHandlers={{
                  click: () => setSelected(atm),
                }}
              >
                <Popup>
                  <div style={{ minWidth: 190 }}>
                    <strong
                      style={{
                        color: COLORS.dark,
                        fontSize: 15,
                      }}
                    >
                      Chase ATM
                    </strong>

                    <p style={{ margin: "7px 0" }}>
                      {atm.address}
                    </p>

                    <p>
                      <strong>
                        {atm.miles.toFixed(1)} miles
                      </strong>{" "}
                      from search center
                    </p>

                    <a
                      href={directionsURL(atm)}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      Get directions
                    </a>
                  </div>
                </Popup>
              </Marker>
            ))}
          </MapContainer>

          {loading && (
            <div
              role="status"
              style={{
                position: "absolute",
                zIndex: 1000,
                top: 16,
                left: "50%",
                transform: "translateX(-50%)",
                background: "white",
                padding: "12px 20px",
                borderRadius: 12,
                boxShadow:
                  "0 5px 20px rgba(0,0,0,.15)",
                display: "flex",
                alignItems: "center",
                gap: 10,
                color: COLORS.dark,
                fontWeight: 600,
              }}
            >
              <RefreshCw size={17} />
              Finding Chase ATMs...
            </div>
          )}
        </div>

        <div
          style={{
            padding: "12px 20px",
            display: "flex",
            flexWrap: "wrap",
            alignItems: "center",
            justifyContent: "space-between",
            gap: 10,
            color: COLORS.muted,
            fontSize: 12,
          }}
        >
          <span>
            Blue numbered pins: mapped Chase ATMs
          </span>
          <span>
            Green dot: search center
          </span>
        </div>
      </section>

      {/* Quick information */}

      {nearest && !loading && (
        <section
          className="panel"
          style={{
            background: COLORS.light,
            borderRadius: 16,
            marginTop: 20,
          }}
        >
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              flexWrap: "wrap",
              gap: 16,
            }}
          >
            <div>
              <small
                style={{
                  color: COLORS.blue,
                  fontWeight: 700,
                }}
              >
                CLOSEST MAPPED CHASE ATM
              </small>

              <h2 style={{ margin: "7px 0" }}>
                {nearest.miles.toFixed(1)} miles away
              </h2>

              <p style={{ marginBottom: 0 }}>
                {nearest.address}
              </p>
            </div>

            <button
              type="button"
              className="button button-primary"
              onClick={() => selectATM(nearest)}
            >
              View on Map
              <ArrowRight size={17} />
            </button>
          </div>
        </section>
      )}

      {/* Results */}

      <section
        className="panel"
        style={{
          marginTop: 20,
          borderRadius: 18,
        }}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            gap: 12,
            marginBottom: 18,
          }}
        >
          <div>
            <span className="eyebrow">
              NEARBY LOCATIONS
            </span>

            <h2 style={{ marginTop: 5 }}>
              Chase ATMs Near You
            </h2>
          </div>

          <strong style={{ color: COLORS.blue }}>
            {results.length} found
          </strong>
        </div>

        {!loading && !results.length && (
          <div
            className="empty"
            style={{ padding: "30px 15px" }}
          >
            <MapPin
              size={34}
              color="#9aaeca"
            />

            <h3>
              No mapped Chase ATMs found
            </h3>

            <p>
              Try searching another area,
              or verify locations using
              Chase's official locator.
            </p>
          </div>
        )}

        <div
          style={{
            display: "grid",
            gap: 12,
          }}
        >
          {results.map((atm) => {
            const active =
              selected?.id === atm.id;

            return (
              <article
                key={atm.id}
                style={{
                  border: active
                    ? `2px solid ${COLORS.blue}`
                    : `1px solid ${COLORS.border}`,
                  borderRadius: 14,
                  padding: 16,
                  background: active
                    ? COLORS.light
                    : "white",
                  display: "flex",
                  gap: 14,
                  flexWrap: "wrap",
                  alignItems: "center",
                }}
              >
                <div
                  style={{
                    background: COLORS.blue,
                    color: "white",
                    width: 40,
                    height: 40,
                    borderRadius: 12,
                    display: "grid",
                    placeItems: "center",
                    fontWeight: 800,
                  }}
                >
                  {atm.number}
                </div>

                <div
                  style={{
                    flex: "1 1 180px",
                  }}
                >
                  <strong
                    style={{
                      display: "block",
                      color: COLORS.dark,
                    }}
                  >
                    Chase ATM
                  </strong>

                  <small
                    style={{
                      display: "block",
                      color: COLORS.blue,
                      marginTop: 4,
                      fontWeight: 600,
                    }}
                  >
                    {atm.kind}
                  </small>

                  <p
                    style={{
                      margin: "7px 0 4px",
                      fontSize: 13,
                    }}
                  >
                    {atm.address}
                  </p>

                  <small
                    style={{
                      color: COLORS.muted,
                    }}
                  >
                    {atm.miles.toFixed(1)} miles away
                  </small>
                </div>

                <div
                  style={{
                    display: "flex",
                    flexWrap: "wrap",
                    gap: 8,
                  }}
                >
                  <button
                    type="button"
                    className="button button-outline"
                    onClick={() => selectATM(atm)}
                  >
                    <MapPin size={16} />
                    View
                  </button>

                  <a
                    className="button button-primary"
                    href={directionsURL(atm)}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    <Navigation size={16} />
                    Directions
                  </a>
                </div>
              </article>
            );
          })}
        </div>
      </section>

      {/* Source note */}

      <section
        className="panel"
        style={{
          marginTop: 20,
          borderRadius: 16,
        }}
      >
        <div
          style={{
            display: "flex",
            alignItems: "flex-start",
            gap: 12,
          }}
        >
          <Building2
            size={23}
            color={COLORS.blue}
          />

          <div>
            <h2>ATM Information</h2>

            <p>
              Locations come from OpenStreetMap
              community data and may be incomplete.
              Showing a location does not guarantee
              its ATM is operational or accessible.
            </p>

            <a
              href="https://locator.chase.com/"
              target="_blank"
              rel="noopener noreferrer"
              className="button button-outline"
            >
              <ExternalLink size={16} />
              Official Chase ATM Locator
            </a>
          </div>
        </div>
      </section>
    </div>
  );
}
