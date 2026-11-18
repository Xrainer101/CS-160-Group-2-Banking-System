import {
  useCallback,
  useEffect,
  useRef,
  useState,
} from "react";

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
  CheckCircle2,
} from "lucide-react";

import "leaflet/dist/leaflet.css";

// =============================================
// CONFIGURATION
// =============================================

const DEFAULT_LOCATION = {
  lat: 37.3382,
  lon: -121.8863,
  label: "San Jose, CA",
};

const SEARCH_RADIUS = 12000; // 12 km
const MAX_RESULTS = 50;

const NOMINATIM_URL =
  "https://nominatim.openstreetmap.org/search";

// Public Overpass servers.
// If one fails, try the next.
const OVERPASS_SERVERS = [
  "https://overpass-api.de/api/interpreter",
  "https://overpass.kumi.systems/api/interpreter",
];

// OpenStreetMap's Wikidata identifier for Chase.
const CHASE_WIKIDATA = "Q524629";

// =============================================
// DISTANCE CALCULATION
// =============================================

function distanceKm(a, b) {
  const toRadians = (value) =>
    (value * Math.PI) / 180;

  const latDifference = toRadians(
    b.lat - a.lat
  );

  const lonDifference = toRadians(
    b.lon - a.lon
  );

  const value =
    Math.sin(latDifference / 2) ** 2 +
    Math.cos(toRadians(a.lat)) *
      Math.cos(toRadians(b.lat)) *
      Math.sin(lonDifference / 2) ** 2;

  return (
    6371 *
    2 *
    Math.atan2(
      Math.sqrt(value),
      Math.sqrt(1 - value)
    )
  );
}

function formatDistance(km) {
  const miles = km * 0.621371;

  return `${miles.toFixed(1)} mi`;
}

// =============================================
// CHASE IDENTIFICATION
// =============================================

function isChase(tags = {}) {
  const identifiableFields = [
    tags.name,
    tags.brand,
    tags.operator,
    tags.network,
    tags["brand:en"],
    tags["operator:en"],
  ];

  const nameMatches = identifiableFields.some(
    (value) =>
      typeof value === "string" &&
      /\bchase\b/i.test(value)
  );

  const wikidataMatches = [
    tags["brand:wikidata"],
    tags["operator:wikidata"],
  ].some(
    (value) =>
      typeof value === "string" &&
      value.split(";").some(
        (part) =>
          part.trim() === CHASE_WIKIDATA
      )
  );

  return nameMatches || wikidataMatches;
}

function isVerifiedChaseATM(tags = {}) {
  if (!isChase(tags)) {
    return false;
  }

  if (tags.amenity === "atm") {
    return true;
  }

  // A Chase branch counts only when
  // its OSM record explicitly says ATM=yes.
  return (
    tags.amenity === "bank" &&
    tags.atm === "yes"
  );
}

// =============================================
// OVERPASS SEARCH
// =============================================

function makeOverpassQuery(lat, lon) {
  const area =
    `(around:${SEARCH_RADIUS},${lat},${lon})`;

  return `
    [out:json][timeout:30];

    (
      nwr${area}
        ["amenity"="atm"]
        ["brand"~"Chase",i];

      nwr${area}
        ["amenity"="atm"]
        ["operator"~"Chase",i];

      nwr${area}
        ["amenity"="atm"]
        ["name"~"Chase",i];

      nwr${area}
        ["amenity"="atm"]
        ["network"~"Chase",i];

      nwr${area}
        ["amenity"="atm"]
        ["brand:wikidata"="${CHASE_WIKIDATA}"];

      nwr${area}
        ["amenity"="atm"]
        ["operator:wikidata"="${CHASE_WIKIDATA}"];

      nwr${area}
        ["amenity"="bank"]
        ["atm"="yes"]
        ["brand"~"Chase",i];

      nwr${area}
        ["amenity"="bank"]
        ["atm"="yes"]
        ["operator"~"Chase",i];

      nwr${area}
        ["amenity"="bank"]
        ["atm"="yes"]
        ["name"~"Chase",i];

      nwr${area}
        ["amenity"="bank"]
        ["atm"="yes"]
        ["brand:wikidata"="${CHASE_WIKIDATA}"];

      nwr${area}
        ["amenity"="bank"]
        ["atm"="yes"]
        ["operator:wikidata"="${CHASE_WIKIDATA}"];
    );

    out center;
  `;
}

// =============================================
// ADDRESS FORMATTING
// =============================================

function formatAddress(tags = {}) {
  if (tags["addr:full"]) {
    return tags["addr:full"];
  }

  const street = [
    tags["addr:housenumber"],
    tags["addr:street"],
  ]
    .filter(Boolean)
    .join(" ");

  const city = [
    tags["addr:city"],
    tags["addr:state"],
    tags["addr:postcode"],
  ]
    .filter(Boolean)
    .join(", ");

  const combined = [
    street,
    city,
  ]
    .filter(Boolean)
    .join(", ");

  return (
    combined ||
    "Street address unavailable"
  );
}

// =============================================
// CONVERT OPENSTREETMAP RESULTS
// =============================================

function toATM(element) {
  const tags = element.tags || {};

  // Reject anything not explicitly identified
  // as a Chase ATM or Chase branch with ATM.
  if (!isVerifiedChaseATM(tags)) {
    return null;
  }

  const lat =
    element.lat ??
    element.center?.lat;

  const lon =
    element.lon ??
    element.center?.lon;

  if (
    !Number.isFinite(lat) ||
    !Number.isFinite(lon)
  ) {
    return null;
  }

  const isBranch =
    tags.amenity === "bank";

  return {
    id: `${element.type}-${element.id}`,

    lat,
    lon,

    name:
      tags.name ||
      (isBranch
        ? "Chase Bank"
        : "Chase ATM"),

    type: isBranch
      ? "Chase branch with ATM"
      : "Chase ATM",

    isBranch,

    address: formatAddress(tags),

    openingHours:
      tags["opening_hours:atm"] ||
      tags.opening_hours ||
      "",

    wheelchair:
      tags.wheelchair || "",

    distance: 0,
  };
}

// =============================================
// API HELPER
// =============================================

async function fetchJSON(
  url,
  options = {},
  timeoutMs = 30000
) {
  const controller =
    new AbortController();

  const timeoutId = setTimeout(
    () => controller.abort(),
    timeoutMs
  );

  try {
    const response = await fetch(
      url,
      {
        ...options,
        signal: controller.signal,
      }
    );

    if (!response.ok) {
      throw new Error(
        `HTTP ${response.status}`
      );
    }

    return await response.json();
  } finally {
    clearTimeout(timeoutId);
  }
}

// =============================================
// OVERPASS FALLBACK
// =============================================

async function fetchChaseATMs(lat, lon) {
  const query = makeOverpassQuery(
    lat,
    lon
  );

  let lastError = null;

  for (const server of OVERPASS_SERVERS) {
    try {
      const response = await fetchJSON(
        server,
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
        35000
      );

      if (!Array.isArray(response.elements)) {
        throw new Error(
          "Invalid ATM search response."
        );
      }

      return response.elements;
    } catch (error) {
      console.warn(
        "Overpass server unavailable:",
        server,
        error
      );

      lastError = error;
    }
  }

  throw (
    lastError ||
    new Error(
      "All ATM search services failed."
    )
  );
}

// =============================================
// SORT AND REMOVE NEAR-DUPLICATES
// =============================================

function processATMs(elements, location) {
  const parsed = elements
    .map(toATM)
    .filter(Boolean)
    .map((atm) => ({
      ...atm,
      distance: distanceKm(
        location,
        atm
      ),
    }))
    .filter(
      (atm) =>
        atm.distance <=
        SEARCH_RADIUS / 1000
    );

  // Keep dedicated ATM nodes ahead of
  // branch-level ATM records at the same place.
  parsed.sort((a, b) => {
    if (a.isBranch !== b.isBranch) {
      return a.isBranch ? 1 : -1;
    }

    return a.distance - b.distance;
  });

  const unique = [];

  for (const atm of parsed) {
    const duplicate = unique.some(
      (existing) =>
        existing.id === atm.id ||
        distanceKm(existing, atm) <
          0.03
    );

    if (!duplicate) {
      unique.push(atm);
    }
  }

  return unique
    .sort(
      (a, b) =>
        a.distance - b.distance
    )
    .slice(0, MAX_RESULTS)
    .map((atm, index) => ({
      ...atm,
      rank: index + 1,
    }));
}

// =============================================
// MARKER ICONS
// =============================================

function makeATMIcon(rank, selected) {
  const background = selected
    ? "#0d275c"
    : "#2459cf";

  const diameter = selected
    ? 44
    : 38;

  return L.divIcon({
    className: "team2-atm-marker",

    html: `
      <div style="
        width:${diameter}px;
        height:${diameter}px;
        background:${background};
        color:#ffffff;
        border:3px solid #ffffff;
        border-radius:50%;
        box-shadow:0 3px 12px rgba(0,0,0,.3);
        display:flex;
        justify-content:center;
        align-items:center;
        font-size:15px;
        font-weight:800;
        position:relative;
      ">
        ${rank}
      </div>
    `,

    iconSize: [
      diameter,
      diameter,
    ],

    iconAnchor: [
      diameter / 2,
      diameter / 2,
    ],

    popupAnchor: [
      0,
      -diameter / 2,
    ],
  });
}

// =============================================
// MAP CONTROLLER
// =============================================

function MapController({
  center,
  results,
  selected,
  markerRefs,
}) {
  const map = useMap();

  useEffect(() => {
    if (!center) return;

    if (results.length > 0) {
      const points = [
        [
          center.lat,
          center.lon,
        ],
        ...results.map(
          (atm) => [
            atm.lat,
            atm.lon,
          ]
        ),
      ];

      map.fitBounds(
        L.latLngBounds(points),
        {
          padding: [35, 35],
          maxZoom: 13,
        }
      );
    } else {
      map.setView(
        [
          center.lat,
          center.lon,
        ],
        12
      );
    }
  }, [map, center, results]);

  useEffect(() => {
    if (!selected) return;

    map.flyTo(
      [
        selected.lat,
        selected.lon,
      ],
      16,
      {
        duration: 0.7,
      }
    );

    const marker =
      markerRefs.current[
        selected.id
      ];

    if (marker) {
      marker.openPopup();
    }
  }, [map, selected, markerRefs]);

  return null;
}

// =============================================
// DIRECTIONS
// =============================================

function directionsURL(atm) {
  return (
    "https://www.openstreetmap.org/directions" +
    `?to=${encodeURIComponent(
      `${atm.lat},${atm.lon}`
    )}`
  );
}

// =============================================
// MAIN COMPONENT
// =============================================

export default function ATMFinder() {
  const [searchText, setSearchText] =
    useState("San Jose, CA");

  const [center, setCenter] =
    useState(DEFAULT_LOCATION);

  const [results, setResults] =
    useState([]);

  const [selected, setSelected] =
    useState(null);

  const [loading, setLoading] =
    useState(true);

  const [error, setError] =
    useState("");

  const [message, setMessage] =
    useState("");

  const requestId = useRef(0);
  const markerRefs = useRef({});

  const locationCache = useRef(
    new Map([
      [
        "san jose, ca",
        DEFAULT_LOCATION,
      ],
    ])
  );

  // ===========================================
  // FETCH NEARBY CHASE ATMS
  // ===========================================

  const searchATMs = useCallback(
    async (location) => {
      const requestNumber =
        ++requestId.current;

      setLoading(true);
      setError("");
      setMessage("");
      setSelected(null);
      setResults([]);
      setCenter(location);

      markerRefs.current = {};

      try {
        const elements =
          await fetchChaseATMs(
            location.lat,
            location.lon
          );

        if (
          requestNumber !==
          requestId.current
        ) {
          return;
        }

        const nearby =
          processATMs(
            elements,
            location
          );

        setResults(nearby);

        if (!nearby.length) {
          setMessage(
            "No explicitly tagged Chase ATMs " +
            "were found within 12 km of " +
            "this location. OpenStreetMap " +
            "may have incomplete ATM data."
          );
        } else {
          setMessage(
            `Found ${nearby.length} nearby ` +
            "Chase ATM locations. " +
            "Select a numbered marker " +
            "or a result below."
          );
        }
      } catch (failure) {
        if (
          requestNumber !==
          requestId.current
        ) {
          return;
        }

        setError(
          "Could not retrieve Chase ATM " +
          "locations. The public map " +
          "service may be unavailable. " +
          "Please try again."
        );

        console.error(
          "ATM search failed:",
          failure
        );
      } finally {
        if (
          requestNumber ===
          requestId.current
        ) {
          setLoading(false);
        }
      }
    },
    []
  );

  // Load San Jose when the page opens.
  useEffect(() => {
    searchATMs(DEFAULT_LOCATION);

    return () => {
      requestId.current += 1;
    };
  }, [searchATMs]);

  // ===========================================
  // SEARCH CITY OR ZIP
  // ===========================================

  async function handleSearch(event) {
    event.preventDefault();

    const query =
      searchText.trim();

    if (!query || loading) {
      return;
    }

    setError("");
    setMessage("");
    setLoading(true);

    try {
      const key =
        query.toLowerCase();

      let location =
        locationCache.current.get(key);

      if (!location) {
        const params =
          new URLSearchParams({
            q: query,
            format: "jsonv2",
            limit: "1",
          });

        const response = await fetchJSON(
          `${NOMINATIM_URL}?${params}`,
          {
            headers: {
              Accept: "application/json",
            },
          },
          15000
        );

        if (!response.length) {
          throw new Error(
            "Location not found. " +
            "Try a city and state " +
            "or ZIP code."
          );
        }

        location = {
          lat: Number(response[0].lat),
          lon: Number(response[0].lon),
          label:
            response[0].display_name,
        };

        if (
          !Number.isFinite(location.lat) ||
          !Number.isFinite(location.lon)
        ) {
          throw new Error(
            "Invalid coordinates returned."
          );
        }

        locationCache.current.set(
          key,
          location
        );
      }

      await searchATMs(location);
    } catch (failure) {
      setError(
        failure.message ||
        "Location search failed."
      );

      setLoading(false);
    }
  }

  // ===========================================
  // NEAR ME
  // ===========================================

  function handleNearMe() {
    if (!navigator.geolocation) {
      setError(
        "Your browser does not " +
        "support location access."
      );

      return;
    }

    setLoading(true);
    setError("");
    setMessage("");

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const location = {
          lat:
            position.coords.latitude,
          lon:
            position.coords.longitude,
          label: "Your location",
        };

        setSearchText(
          "My current location"
        );

        searchATMs(location);
      },

      (failure) => {
        setLoading(false);

        setError(
          "Could not access your location: " +
          failure.message +
          ". Try searching by ZIP code."
        );
      },

      {
        enableHighAccuracy: false,
        timeout: 15000,
      }
    );
  }

  // ===========================================
  // SELECT ATM
  // ===========================================

  function selectATM(atm) {
    setSelected(atm);

    document
      .getElementById("chase-atm-map")
      ?.scrollIntoView({
        behavior: "smooth",
        block: "center",
      });
  }

  // ===========================================
  // UI
  // ===========================================

  return (
    <>
      <div className="page-heading">
        <span className="eyebrow">
          ATM LOCATOR
        </span>

        <h1>
          Find Chase ATMs Near You
        </h1>

        <p>
          Explore Chase ATM locations on
          our interactive map and find
          the closest ATM to your location.
        </p>
      </div>

      {/* SEARCH SECTION */}

      <section className="panel">
        <h2>
          <MapPin
            size={22}
            style={{
              display: "inline",
              verticalAlign: "middle",
              marginRight: 8,
            }}
          />
          Search Chase ATMs
        </h2>

        <p className="panel-subtitle">
          Enter a city, ZIP code, or address.
          Only locations explicitly identified
          as Chase ATMs are included.
        </p>

        <form
          onSubmit={handleSearch}
          style={{
            display: "flex",
            gap: 12,
            flexWrap: "wrap",
            alignItems: "end",
          }}
        >
          <label
            style={{
              flex: "1 1 240px",
            }}
          >
            Search location

            <input
              value={searchText}
              onChange={(event) =>
                setSearchText(
                  event.target.value
                )
              }
              placeholder="San Jose, CA"
              style={{
                width: "100%",
                padding: 12,
              }}
            />
          </label>

          <button
            type="submit"
            className="button button-primary"
            disabled={loading}
          >
            <Search size={17} />
            Search
          </button>

          <button
            type="button"
            className="button button-outline"
            onClick={handleNearMe}
            disabled={loading}
          >
            <LocateFixed size={17} />
            Near Me
          </button>
        </form>

        {loading && (
          <p
            role="status"
            style={{ marginTop: 15 }}
          >
            <RefreshCw
              size={17}
              style={{
                display: "inline",
                marginRight: 8,
              }}
            />
            Finding nearby Chase ATMs...
          </p>
        )}

        {error && (
          <p
            className="form-error"
            role="alert"
            style={{ marginTop: 15 }}
          >
            {error}
          </p>
        )}

        {message && !loading && (
          <p
            role="status"
            style={{
              marginTop: 15,
              color: "#31588f",
            }}
          >
            <CheckCircle2
              size={17}
              style={{
                display: "inline",
                verticalAlign: "middle",
                marginRight: 8,
              }}
            />
            {message}
          </p>
        )}
      </section>

      {/* MAP SECTION */}

      <section
        className="panel"
        id="chase-atm-map"
      >
        <div
          style={{
            display: "flex",
            justifyContent:
              "space-between",
            alignItems: "center",
            flexWrap: "wrap",
            gap: 12,
            marginBottom: 18,
          }}
        >
          <div>
            <h2 style={{ margin: 0 }}>
              Nearby Chase ATM Map
            </h2>

            <small>
              Blue numbered markers
              identify matching Chase
              ATM locations.
            </small>
          </div>

          <strong
            style={{
              padding: "9px 14px",
              background: "#eaf0ff",
              color: "#2459cf",
              borderRadius: 20,
            }}
          >
            {results.length} locations
          </strong>
        </div>

        <div
          style={{
            width: "100%",
            height: 490,
            borderRadius: 14,
            overflow: "hidden",
            border:
              "1px solid #dce5f1",
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
              results={results}
              selected={selected}
              markerRefs={markerRefs}
            />

            {/* Search center */}
            <CircleMarker
              center={[
                center.lat,
                center.lon,
              ]}
              radius={8}
              pathOptions={{
                color: "#ffffff",
                weight: 3,
                fillColor: "#16a085",
                fillOpacity: 1,
              }}
            >
              <Popup>
                <strong>
                  Search center
                </strong>

                <p>
                  {center.label}
                </p>
              </Popup>
            </CircleMarker>

            {/* Chase ATM markers */}
            {results.map((atm) => (
              <Marker
                key={atm.id}
                position={[
                  atm.lat,
                  atm.lon,
                ]}
                icon={makeATMIcon(
                  atm.rank,
                  selected?.id === atm.id
                )}
                ref={(marker) => {
                  if (marker) {
                    markerRefs.current[
                      atm.id
                    ] = marker;
                  } else {
                    delete markerRefs.current[
                      atm.id
                    ];
                  }
                }}
                eventHandlers={{
                  click: () =>
                    setSelected(atm),
                }}
              >
                <Popup>
                  <div
                    style={{
                      minWidth: 190,
                    }}
                  >
                    <strong>
                      {atm.rank}. {atm.name}
                    </strong>

                    <p>
                      {atm.type}
                    </p>

                    <p>
                      {atm.address}
                    </p>

                    <strong>
                      {formatDistance(
                        atm.distance
                      )}{" "}
                      from search center
                    </strong>

                    <p>
                      <a
                        href={directionsURL(atm)}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        Get directions
                      </a>
                    </p>
                  </div>
                </Popup>
              </Marker>
            ))}
          </MapContainer>
        </div>

        <p
          style={{
            marginTop: 12,
            fontSize: 13,
            color: "#718096",
          }}
        >
          <strong>Map legend:</strong>{" "}
          Blue numbers = Chase ATM locations.
          Dark blue = selected ATM.
          Green dot = search center.
          Distances are straight-line estimates.
        </p>
      </section>

      {/* ATM RESULTS */}

      <section className="panel">
        <h2>
          Closest Chase ATMs
        </h2>

        <p className="panel-subtitle">
          Sorted from nearest to farthest,
          within approximately 12 km
          of your selected location.
        </p>

        {!loading &&
          !error &&
          results.length === 0 && (
            <p className="empty">
              No confirmed Chase ATM
              locations found in this area.
              Try another city or ZIP code.
            </p>
          )}

        <div
          style={{
            display: "grid",
            gap: 14,
          }}
        >
          {results.map((atm) => {
            const active =
              selected?.id === atm.id;

            return (
              <article
                key={atm.id}
                style={{
                  display: "flex",
                  alignItems:
                    "flex-start",
                  gap: 14,
                  flexWrap: "wrap",
                  padding: 17,
                  borderRadius: 12,
                  border: active
                    ? "2px solid #2459cf"
                    : "1px solid #e0e7f0",
                  background: active
                    ? "#edf3ff"
                    : "#fff",
                }}
              >
                {/* Numbered location indicator */}

                <div
                  style={{
                    width: 40,
                    height: 40,
                    borderRadius: "50%",
                    background: active
                      ? "#0d275c"
                      : "#2459cf",
                    display: "grid",
                    placeItems: "center",
                    color: "white",
                    fontWeight: 800,
                    flexShrink: 0,
                  }}
                >
                  {atm.rank}
                </div>

                <div
                  style={{
                    flex: "1 1 180px",
                  }}
                >
                  <strong
                    style={{
                      display: "block",
                      fontSize: 16,
                    }}
                  >
                    {atm.name}
                  </strong>

                  <small
                    style={{
                      display: "block",
                      marginTop: 5,
                      color: "#2459cf",
                      fontWeight: 700,
                    }}
                  >
                    {atm.type}
                  </small>

                  <p
                    style={{
                      marginTop: 8,
                      marginBottom: 5,
                    }}
                  >
                    {atm.address}
                  </p>

                  <small
                    style={{
                      color: "#718096",
                    }}
                  >
                    <MapPin
                      size={14}
                      style={{
                        display: "inline",
                        verticalAlign:
                          "middle",
                        marginRight: 4,
                      }}
                    />
                    {formatDistance(
                      atm.distance
                    )}{" "}
                    away
                  </small>
                </div>

                <div
                  style={{
                    display: "flex",
                    gap: 8,
                    flexWrap: "wrap",
                  }}
                >
                  <button
                    type="button"
                    className="button button-outline"
                    onClick={() =>
                      selectATM(atm)
                    }
                  >
                    <MapPin size={16} />
                    Show on Map
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
              </article>
            );
          })}
        </div>
      </section>

      {/* CHASE FALLBACK */}

      <section className="panel">
        <h2>
          Verify ATM Information
        </h2>

        <p>
          ATM information comes from
          OpenStreetMap community data.
          Some locations may be missing
          or outdated. A mapped ATM does
          not guarantee it is currently
          operating or accessible.
        </p>

        <a
          className="button button-outline"
          href="https://locator.chase.com/"
          target="_blank"
          rel="noopener noreferrer"
        >
          <ExternalLink size={17} />
          Verify with Chase's Official Locator
        </a>
      </section>
    </>
  );
}
