import React, { useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { STATUS_COLOR, STATUS_LABEL } from './contractorData';

const createPinIcon = (color) => L.divIcon({
  className: 'bevest-osm-pin',
  html: `
    <div style="
      position: relative;
      width: 30px;
      height: 38px;
      display: flex;
      align-items: center;
      justify-content: center;
      filter: drop-shadow(0 2px 4px rgba(15, 23, 42, 0.35));
    ">
      <svg width="30" height="38" viewBox="0 0 30 38" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M15 1C7.26801 1 1 7.26801 1 15C1 24.25 15 36.5 15 36.5C15 36.5 29 24.25 29 15C29 7.26801 22.732 1 15 1Z" fill="${color}" stroke="#FFFFFF" stroke-width="2.5"/>
        <circle cx="15" cy="14.5" r="5" fill="#FFFFFF"/>
      </svg>
    </div>
  `,
  iconSize: [30, 38],
  iconAnchor: [15, 37],
  popupAnchor: [0, -34],
});

/**
 * Reusable OpenStreetMap component powered by Leaflet (100% free, no API key required).
 *
 * @param {Array} pins - [{ id, latitude, longitude, title, status, subtitle, stale }]
 * @param {number} height - Map height in px (default 240)
 * @param {number} zoom - Initial zoom for single pin (default 17)
 */
const OsmMap = ({ pins = [], height = 240, zoom = 17 }) => {
  const containerRef = useRef(null);
  const mapRef = useRef(null);
  const markersLayerRef = useRef(null);
  const hasCenteredRef = useRef(false);

  const validPins = pins.filter(
    (p) => p && Number.isFinite(p.latitude) && Number.isFinite(p.longitude)
      && (Math.abs(p.latitude) > 0.0001 || Math.abs(p.longitude) > 0.0001),
  );

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return undefined;

    const first = validPins[0];
    const initialCenter = first ? [first.latitude, first.longitude] : [14.5995, 120.9842];

    const map = L.map(containerRef.current, {
      center: initialCenter,
      zoom,
      zoomControl: true,
      attributionControl: true,
    });

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap contributors',
    }).addTo(map);

    const layerGroup = L.layerGroup().addTo(map);
    mapRef.current = map;
    markersLayerRef.current = layerGroup;

    // Ensure proper tile sizing if rendered inside a modal
    const timer = setTimeout(() => {
      map.invalidateSize();
    }, 150);

    return () => {
      clearTimeout(timer);
      map.remove();
      mapRef.current = null;
      markersLayerRef.current = null;
      hasCenteredRef.current = false;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    const layer = markersLayerRef.current;
    if (!map || !layer) return;

    layer.clearLayers();
    if (validPins.length === 0) return;

    const latLngs = [];
    validPins.forEach((pin) => {
      const color = STATUS_COLOR[pin.status] || STATUS_COLOR.NORMAL;
      const statusText = STATUS_LABEL[pin.status] || 'Normal';
      const marker = L.marker([pin.latitude, pin.longitude], {
        icon: createPinIcon(color),
      });

      const popupHtml = `
        <div style="font-family: system-ui, sans-serif; min-width: 140px;">
          <strong style="font-size: 0.9rem; color: #0f172a; display: block; margin-bottom: 2px;">
            ${pin.title || pin.id}
          </strong>
          <span style="font-size: 0.78rem; font-weight: 600; color: ${color};">
            ${statusText}${pin.stale ? ' · Last known' : ''}
          </span>
          ${pin.subtitle ? `<div style="font-size: 0.75rem; color: #475569; margin-top: 3px;">${pin.subtitle}</div>` : ''}
          <div style="font-size: 0.72rem; color: #64748b; margin-top: 3px;">
            ${pin.latitude.toFixed(6)}, ${pin.longitude.toFixed(6)}
          </div>
        </div>
      `;
      marker.bindPopup(popupHtml);
      marker.addTo(layer);
      latLngs.push([pin.latitude, pin.longitude]);
    });

    map.invalidateSize();

    if (validPins.length === 1) {
      map.setView(latLngs[0], hasCenteredRef.current ? map.getZoom() : zoom, { animate: true });
      hasCenteredRef.current = true;
    } else if (!hasCenteredRef.current && latLngs.length > 1) {
      map.fitBounds(latLngs, { padding: [36, 36], maxZoom: 17 });
      hasCenteredRef.current = true;
    }
  }, [JSON.stringify(validPins), zoom]);

  return (
    <div
      ref={containerRef}
      style={{
        width: '100%',
        height: `${height}px`,
        borderRadius: '12px',
        overflow: 'hidden',
        border: '1px solid #e2e8f0',
        zIndex: 1,
      }}
    />
  );
};

export default OsmMap;

