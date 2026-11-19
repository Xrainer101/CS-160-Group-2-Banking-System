
import { useEffect, useRef, useState } from "react";
import {
  MapContainer,
  TileLayer,
  Marker,
  Popup,
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
} from "lucide-react";

import "leaflet/dist/leaflet.css";

const DEFAULT_LOCATION = {
  lat: 37.3382,
  lon: -121.8863,
  label: "San Jose, CA",
};

const SEARCH_RADIUS = 30; // meters
const NOMINATIM_URL =
  "https://nominatim.openstreetmap.org/search";
const OVERPASS_URL =
//   "https://overpass.kumi.systems/api/interpreter";
    "https://overpass.private.coffee/api/interpreter";

const atmIcon = L.divIcon({
  className: "team2-atm-pin",
  html: `
    <div style="
      width:34px;
      height:34px;
      border-radius:50%;
      background:#2656c4;
      border:3px solid white;
      box-shadow:0 3px 10px rgba(0,0,0,.25);
      display:flex;
      align-items:center;
      justify-content:center;
      color:white;
      font-size:17px;
      font-weight:bold;
    ">
      $
    </div>
  `,
  iconSize: [34, 34],
  iconAnchor: [17, 17],
});

function MapController({ center, selected, results }) {
  const map = useMap();

  useEffect(() => {
    if (!center) return;

    map.setView(
      [center.lat, center.lon],
      12
    );
  }, [map, center]);

  useEffect(() => {
    if (!selected) return;

    map.setView(
      [selected.lat, selected.lon],
      16
    );
  }, [map, selected]);

  return null;
}

function formatAddress(tags) {
  const street = [
    tags["addr:housenumber"],
    tags["addr:street"],
  ].filter(Boolean).join(" ");

  const city = [
    tags["addr:city"],
    tags["addr:state"],
    tags["addr:postcode"],
  ].filter(Boolean).join(", ");

  return (
    [street, city].filter(Boolean).join(", ") ||
    tags["addr:full"] ||
    "Address not listed in OpenStreetMap"
  );
}

function makeOverpassQuery(lat, lon) {
  const area =
    `(around:${SEARCH_RADIUS},${lat},${lon})`;

  return `
    [out:json][timeout:25];
    (
      nwr${area}["amenity"="atm"]["brand"~"Chase",i];
      nwr${area}["amenity"="atm"]["operator"~"Chase",i];
      nwr${area}["amenity"="atm"]["name"~"Chase",i];
      nwr${area}["amenity"="bank"]["brand"~"Chase",i]["atm"="yes"];
      nwr${area}["amenity"="bank"]["name"~"Chase",i]["atm"="yes"];
    );
    out center;
  `;
}

function toATM(element) {
  const lat =
    element.lat ?? element.center?.lat;

  const lon =
    element.lon ?? element.center?.lon;

  if (
    !Number.isFinite(lat) ||
    !Number.isFinite(lon)
  ) {
    return null;
  }

  const tags = element.tags || {};

  return {
    id: `${element.type}-${element.id}`,
    lat,
    lon,
    name:
      tags.name ||
      tags.brand ||
      tags.operator ||
      "Chase ATM",
    address: formatAddress(tags),
    wheelchair: tags.wheelchair,
    openingHours: tags.opening_hours,
    type:
      tags.amenity === "bank"
        ? "Chase branch with ATM"
        : "Chase ATM",
  };
}

async function fetchWithTimeout(
  url,
  options = {},
  timeout = 30000
) {
  const controller = new AbortController();

  const timeoutId = setTimeout(
    () => controller.abort(),
    timeout
  );

  try {
    const response = await fetch(url, {
      ...options,
      signal: controller.signal,
    });

    if (!response.ok) {
      throw new Error(
        `Map service returned HTTP ${response.status}`
      );
    }

    return await response.json();
  } finally {
    clearTimeout(timeoutId);
  }
}

function distanceKm(first, second) {
  const radians = (degrees) =>
    (degrees * Math.PI) / 180;

  const deltaLat = radians(
    second.lat - first.lat
  );

  const deltaLon = radians(
    second.lon - first.lon
  );

  const a =
    Math.sin(deltaLat / 2) ** 2 +
    Math.cos(radians(first.lat)) *
      Math.cos(radians(second.lat)) *
      Math.sin(deltaLon / 2) ** 2;

  return 6371 * 2 * Math.atan2(
    Math.sqrt(a),
    Math.sqrt(1 - a)
  );
}

function directionsURL(atm) {
  // Opens directions in OpenStreetMap.
  // No Google API key is required.
  return (
    "https://www.openstreetmap.org/directions" +
    `?to=${encodeURIComponent(
      `${atm.lat},${atm.lon}`
    )}`
  );
}

export default function ATMFinder() {
  const [searchText, setSearchText] =
    useState("San Jose, CA");

  const [center, setCenter] =
    useState(DEFAULT_LOCATION);

  const [selected, setSelected] =
    useState(null);

  const [results, setResults] =
    useState([]);

  const [loading, setLoading] =
    useState(false);

  const [error, setError] =
    useState("");

  const [message, setMessage] =
    useState("");

  const requestId = useRef(0);
  const lastGeocode = useRef({
    query: "",
    result: null,
    timestamp: 0,
  });

  async function searchATMs(location) {
    const id = ++requestId.current;

    setLoading(true);
    setError("");
    setMessage("");
    setSelected(null);

    try {
      const query = makeOverpassQuery(
        location.lat,
        location.lon
      );

      const response = await fetchWithTimeout(
        OVERPASS_URL,
        {
          method: "POST",
          headers: {
            "Content-Type":
              "application/x-www-form-urlencoded",
          },
          body:
            new URLSearchParams({
              data: query,
            }).toString(),
        },
        60000
      );

      if (id !== requestId.current) return;

      const parsed = (
        response.elements || []
      )
        .map(toATM)
        .filter(Boolean);

      const unique = Array.from(
        new Map(
          parsed.map((atm) => [atm.id, atm])
        ).values()
      );

      const sorted = unique.sort(
        (a, b) =>
          distanceKm(location, a) -
          distanceKm(location, b)
      );

      setResults(sorted);
      setCenter(location);

      if (!sorted.length) {
        setMessage(
          "No Chase ATM records were found " +
          "in OpenStreetMap for this area. " +
          "Try another location or check " +
          "Chase's official locator."
        );
      }
    } catch (err) {
      if (id !== requestId.current) return;

      setError(
        err.name === "AbortError"
          ? "The map search timed out. Please retry."
          : "Unable to load ATM data. The public " +
              "Overpass service may be busy. " +
              (err.message || "")
      );
    } finally {
      if (id === requestId.current) {
        setLoading(false);
      }
    }
  }

  useEffect(() => {
    // Load the default San Jose area once.
    searchATMs(DEFAULT_LOCATION);

    return () => {
      requestId.current += 1;
    };
  }, []);

  async function handleSearch(event) {
    event.preventDefault();

    const query = searchText.trim();

    if (!query || loading) return;

    setError("");
    setLoading(true);

    try {
      let place;

      const cache = lastGeocode.current;

      if (
        cache.query.toLowerCase() ===
          query.toLowerCase() &&
        cache.result &&
        Date.now() - cache.timestamp <
          60 * 60 * 1000
      ) {
        place = cache.result;
      } else {
        const params =
          new URLSearchParams({
            q: query,
            format: "jsonv2",
            limit: "1",
          });

        const url =
          `${NOMINATIM_URL}?${params.toString()}`;

        const locations =
          await fetchWithTimeout(url);

        if (!locations.length) {
          throw new Error(
            "No matching city or address found."
          );
        }

        place = {
          lat: Number(locations[0].lat),
          lon: Number(locations[0].lon),
          label: locations[0].display_name,
        };

        lastGeocode.current = {
          query,
          result: place,
          timestamp: Date.now(),
        };
      }

      await searchATMs(place);
    } catch (err) {
      setError(
        err.message ||
          "Could not find this location."
      );
    } finally {
      setLoading(false);
    }
  }

  function useMyLocation() {
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
        const location = {
          lat: position.coords.latitude,
          lon: position.coords.longitude,
          label: "My current location",
        };

        setSearchText("My current location");
        searchATMs(location);
      },
      (err) => {
        setLoading(false);
        setError(
          "Location unavailable: " +
            err.message +
            ". Enter a city or ZIP code instead."
        );
      },
      {
        enableHighAccuracy: false,
        timeout: 12000,
      }
    );
  }

  return (
    <>
      <div className="page-heading">
        <span className="eyebrow">
          ATM LOCATOR
        </span>
        <h1>Find Chase ATMs</h1>
        <p>
          Search for Chase ATMs near you using
          OpenStreetMap.
        </p>
      </div>

      <section className="panel">
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 12,
            marginBottom: 18,
          }}
        >
          <MapPin color="#2855c8" />
          <div>
            <h2 style={{ margin: 0 }}>
              Find a nearby ATM
            </h2>
            <small>
              Search by city, ZIP code, or address.
            </small>
          </div>
        </div>

        <form
          onSubmit={handleSearch}
          style={{
            display: "flex",
            gap: 12,
            alignItems: "end",
            flexWrap: "wrap",
          }}
        >
          <label
            style={{
              flex: "1 1 250px",
            }}
          >
            Search location
            <input
              value={searchText}
              onChange={(event) =>
                setSearchText(event.target.value)
              }
              placeholder="e.g. San Jose, CA"
              style={{
                width: "100%",
                padding: 12,
              }}
            />
          </label>

          <button
            className="button button-primary"
            type="submit"
            disabled={loading}
          >
            <Search size={17} />
            Search
          </button>

          <button
            className="button button-outline"
            type="button"
            disabled={loading}
            onClick={useMyLocation}
          >
            <LocateFixed size={17} />
            Near me
          </button>
        </form>

        {error && (
          <p
            className="form-error"
            role="alert"
            style={{ marginTop: 16 }}
          >
            {error}
          </p>
        )}

        {message && (
          <p
            role="status"
            style={{ marginTop: 16 }}
          >
            {message}
          </p>
        )}

        {loading && (
          <p
            role="status"
            style={{ marginTop: 16 }}
          >
            <RefreshCw
              size={17}
              style={{
                display: "inline",
                verticalAlign: "middle",
                marginRight: 8,
              }}
            />
            Searching for Chase ATMs...
          </p>
        )}

        <div
          style={{
            width: "100%",
            height: 470,
            marginTop: 20,
            borderRadius: 14,
            overflow: "hidden",
            border: "1px solid #e0e6ef",
          }}
        >
          <MapContainer
            center={[
              DEFAULT_LOCATION.lat,
              DEFAULT_LOCATION.lon,
            ]}
            zoom={12}
            scrollWheelZoom={false}
            style={{
              width: "100%",
              height: "100%",
            }}
          >
            <TileLayer
              url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>'
              maxZoom={19}
            />

            <MapController
              center={center}
              selected={selected}
              results={results}
            />

            {results.map((atm) => (
              <Marker
                key={atm.id}
                position={[
                  atm.lat,
                  atm.lon,
                ]}
                icon={atmIcon}
                eventHandlers={{
                  click: () =>
                    setSelected(atm),
                }}
              >
                <Popup>
                  <strong>{atm.name}</strong>

                  <p>
                    {atm.type}
                  </p>

                  <p>
                    {atm.address}
                  </p>

                  <a
                    href={directionsURL(atm)}
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Get directions
                  </a>
                </Popup>
              </Marker>
            ))}
          </MapContainer>
        </div>
      </section>

      <section className="panel">
        <h2>
          Chase ATM Results ({results.length})
        </h2>

        {results.length === 0 && (
          <p className="empty">
            No ATM locations to display.
          </p>
        )}

        <div className="summary-list">
          {results.map((atm) => (
            <div
              className="summary-row"
              key={atm.id}
              style={{
                gap: 15,
                alignItems: "center",
                background:
                  selected?.id === atm.id
                    ? "#edf3ff"
                    : "transparent",
              }}
            >
              <span style={{ flex: 1 }}>
                <strong>
                  {atm.name}
                </strong>

                <small
                  style={{
                    display: "block",
                    marginTop: 5,
                  }}
                >
                  {atm.type} ·{" "}
                  {distanceKm(center, atm)
                    .toFixed(1)}{" "}
                  km away
                </small>

                <small
                  style={{
                    display: "block",
                    marginTop: 5,
                  }}
                >
                  {atm.address}
                </small>
              </span>

              <button
                type="button"
                className="button button-outline"
                onClick={() =>
                  setSelected(atm)
                }
              >
                Show on map
              </button>

              <a
                href={directionsURL(atm)}
                target="_blank"
                rel="noopener noreferrer"
                className="button button-primary"
              >
                <Navigation size={16} />
                Directions
              </a>
            </div>
          ))}
        </div>
      </section>

      <section className="panel">
        <h2>Verify ATM information</h2>

        <p>
          OpenStreetMap is community maintained.
          Some Chase ATMs may not appear, and
          operating hours or availability may
          be incomplete.
        </p>

        <a
          className="button button-outline"
          href="https://locator.chase.com/"
          target="_blank"
          rel="noopener noreferrer"
        >
          <ExternalLink size={17} />
          Open official Chase locator
        </a>
      </section>
    </>
  );
}
