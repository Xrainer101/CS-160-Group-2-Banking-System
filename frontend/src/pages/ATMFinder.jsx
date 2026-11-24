
import { useState } from "react";
import {
  MapPin,
  Search,
  Navigation,
  ExternalLink,
  ShieldCheck,
  LocateFixed,
} from "lucide-react";

// ======================================================
// ATM FINDER
// Opens Google Maps with nearby Chase ATM search results
// No API key or backend required.
// ======================================================

const GOOGLE_MAPS_SEARCH_URL =
  "https://www.google.com/maps/search/?api=1";

function buildGoogleMapsURL(location) {
  const query = `Chase ATM near ${location}`;

  const params = new URLSearchParams({
    query,
  });

  return `${GOOGLE_MAPS_SEARCH_URL}&${params.toString()}`;
}

export default function ATMFinder() {
  const [city, setCity] = useState("");
  const [error, setError] = useState("");

  // ----------------------------------------------------
  // Search for Chase ATMs in the entered city
  // ----------------------------------------------------

  function handleLocate(event) {
    event.preventDefault();

    const location = city.trim();

    if (!location) {
      setError(
        "Please enter your city, state, or ZIP code."
      );
      return;
    }

    setError("");

    // Google Maps performs the ATM search.
    // Opens in a separate browser tab.
    const googleMapsURL = buildGoogleMapsURL(location);

    window.open(
      googleMapsURL,
      "_blank",
      "noopener,noreferrer"
    );
  }

  // ----------------------------------------------------
  // Optional: Find ATMs using current location
  // ----------------------------------------------------

  function handleNearMe() {
    setError("");

    if (!navigator.geolocation) {
      setError(
        "Your browser does not support location services."
      );
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const { latitude, longitude } =
          position.coords;

        const location =
          `${latitude},${longitude}`;

        const params = new URLSearchParams({
          query: "Chase ATM",
        });

        // Google Maps can center around coordinates.
        const url =
          `https://www.google.com/maps/search/Chase+ATM/` +
          `@${latitude},${longitude},13z`;

        window.open(
          url,
          "_blank",
          "noopener,noreferrer"
        );
      },
      (geoError) => {
        setError(
          "Unable to access your location. " +
          "Please enter your city instead."
        );
      },
      {
        enableHighAccuracy: false,
        timeout: 12000,
      }
    );
  }

  // ====================================================
  // USER INTERFACE
  // ====================================================

  return (
    <div style={{ paddingBottom: 32 }}>
      {/* PAGE HEADER */}

      <div className="page-heading">
        <span className="eyebrow">
          ATM LOCATOR
        </span>

        <h1>Find Chase ATMs</h1>

        <p>
          Find Chase ATMs in your area quickly and
          easily. Enter your location to explore
          nearby ATMs and get directions.
        </p>
      </div>

      {/* MAIN ATM SEARCH CARD */}

      <section
        className="panel"
        style={{
          maxWidth: 850,
          margin: "0 auto 24px",
          borderRadius: 20,
          padding: "36px 30px",
        }}
      >
        {/* ICON HEADER */}

        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 16,
            marginBottom: 28,
          }}
        >
          <div
            style={{
              width: 58,
              height: 58,
              borderRadius: 16,
              background: "#eaf1ff",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              flexShrink: 0,
            }}
          >
            <MapPin
              size={28}
              color="#2458c6"
            />
          </div>

          <div>
            <h2 style={{ margin: "0 0 5px" }}>
              Locate a Chase ATM
            </h2>

            <p
              style={{
                margin: 0,
                fontSize: 14,
                color: "#64748b",
              }}
            >
              Search for Chase ATMs near
              your preferred location.
            </p>
          </div>
        </div>

        {/* LOCATION SEARCH */}

        <form onSubmit={handleLocate}>
          <label
            htmlFor="atm-city"
            style={{
              display: "block",
              fontWeight: 600,
              marginBottom: 10,
            }}
          >
            Enter your city, state, or ZIP code
          </label>

          <div
            style={{
              display: "flex",
              gap: 12,
              flexWrap: "wrap",
              alignItems: "stretch",
            }}
          >
            <div
              style={{
                flex: "1 1 260px",
                position: "relative",
              }}
            >
              <MapPin
                size={20}
                color="#64748b"
                style={{
                  position: "absolute",
                  left: 15,
                  top: "50%",
                  transform: "translateY(-50%)",
                }}
              />

              <input
                id="atm-city"
                type="text"
                value={city}
                onChange={(event) => {
                  setCity(event.target.value);
                  setError("");
                }}
                placeholder="e.g. San Jose, CA"
                autoComplete="address-level2"
                style={{
                  width: "100%",
                  padding: "15px 16px 15px 46px",
                  fontSize: 15,
                  borderRadius: 12,
                }}
              />
            </div>

            <button
              type="submit"
              className="button button-primary"
              style={{
                padding: "14px 24px",
                borderRadius: 12,
                fontWeight: 700,
                whiteSpace: "nowrap",
              }}
            >
              <Search size={18} />
              Locate Chase ATMs
              <ExternalLink size={16} />
            </button>
          </div>

          {error && (
            <p
              className="form-error"
              role="alert"
              style={{ marginTop: 14 }}
            >
              {error}
            </p>
          )}
        </form>

        {/* OPTIONAL CURRENT LOCATION SEARCH */}

        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 16,
            margin: "25px 0",
          }}
        >
          <div
            style={{
              height: 1,
              background: "#e2e8f0",
              flex: 1,
            }}
          />

          <span
            style={{
              color: "#94a3b8",
              fontSize: 13,
              fontWeight: 600,
            }}
          >
            OR
          </span>

          <div
            style={{
              height: 1,
              background: "#e2e8f0",
              flex: 1,
            }}
          />
        </div>

        <button
          type="button"
          className="button button-outline"
          onClick={handleNearMe}
          style={{
            width: "100%",
            justifyContent: "center",
            padding: 14,
            borderRadius: 12,
          }}
        >
          <LocateFixed size={19} />
          Find Chase ATMs Near My Current Location
        </button>

        {/* INFO NOTE */}

        <div
          style={{
            marginTop: 28,
            padding: 18,
            background: "#f1f6ff",
            borderRadius: 14,
            display: "flex",
            gap: 13,
            alignItems: "flex-start",
          }}
        >
          <ShieldCheck
            size={21}
            color="#2458c6"
            style={{ flexShrink: 0 }}
          />

          <div>
            <strong
              style={{
                display: "block",
                fontSize: 14,
                color: "#183b72",
                marginBottom: 6,
              }}
            >
              Find ATMs with Google Maps
            </strong>

            <p
              style={{
                margin: 0,
                fontSize: 13,
                lineHeight: 1.7,
                color: "#526781",
              }}
            >
              Clicking Locate Chase ATMs will
              open Google Maps in a new tab,
              showing Chase ATM search results
              around your selected location.
              You can view nearby locations,
              get directions, and explore
              additional ATM information.
            </p>
          </div>
        </div>
      </section>

      {/* HOW IT WORKS */}

      <section
        className="panel"
        style={{
          maxWidth: 850,
          margin: "0 auto 24px",
          borderRadius: 20,
          padding: 28,
        }}
      >
        <h2 style={{ marginTop: 0 }}>
          How It Works
        </h2>

        <div
          style={{
            display: "grid",
            gridTemplateColumns:
              "repeat(auto-fit, minmax(180px, 1fr))",
            gap: 24,
            marginTop: 22,
          }}
        >
          {[
            {
              number: "01",
              title: "Enter Location",
              text: "Type your city, state, or ZIP code.",
              Icon: MapPin,
            },
            {
              number: "02",
              title: "Locate ATMs",
              text: "We'll open Google Maps with a Chase ATM search.",
              Icon: Search,
            },
            {
              number: "03",
              title: "Get Directions",
              text: "Choose an ATM and navigate directly to it.",
              Icon: Navigation,
            },
          ].map(
            ({ number, title, text, Icon }) => (
              <div key={number}>
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: 12,
                    marginBottom: 13,
                  }}
                >
                  <div
                    style={{
                      background: "#eaf1ff",
                      borderRadius: 12,
                      width: 42,
                      height: 42,
                      display: "flex",
                      justifyContent: "center",
                      alignItems: "center",
                    }}
                  >
                    <Icon
                      size={20}
                      color="#2458c6"
                    />
                  </div>

                  <span
                    style={{
                      color: "#94a3b8",
                      fontSize: 13,
                      fontWeight: 700,
                    }}
                  >
                    {number}
                  </span>
                </div>

                <strong
                  style={{
                    display: "block",
                    marginBottom: 6,
                    color: "#142b55",
                  }}
                >
                  {title}
                </strong>

                <p
                  style={{
                    margin: 0,
                    fontSize: 13,
                    lineHeight: 1.6,
                    color: "#64748b",
                  }}
                >
                  {text}
                </p>
              </div>
            )
          )}
        </div>
      </section>
    </div>
  );
}
