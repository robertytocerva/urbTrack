#!/usr/bin/env python3
"""Genera ubicaciones periódicas usando POST /api/units/me/location."""

import argparse
import json
import math
import os
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone

EARTH_RADIUS_METERS = 6_371_000.0


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default=os.getenv("BASE_URL", "http://localhost:8080"))
    parser.add_argument("--token", default=os.getenv("UNIT_TOKEN"), help="o define UNIT_TOKEN en el entorno")
    parser.add_argument("--latitude", type=float, required=True, help="latitud inicial de la unidad")
    parser.add_argument("--longitude", type=float, required=True, help="longitud inicial de la unidad")
    parser.add_argument("--events", type=int, default=10, help="número de ubicaciones a enviar")
    parser.add_argument("--interval-seconds", type=float, default=3.0, help="pausa entre ubicaciones")
    parser.add_argument("--step-meters", type=float, default=20.0, help="distancia entre puntos")
    parser.add_argument("--bearing-degrees", type=float, default=45.0, help="rumbo constante en grados")
    parser.add_argument("--speed-kmh", type=float, default=15.0, help="velocidad incluida en cada ubicación")
    args = parser.parse_args()

    if not args.token:
        parser.error("define UNIT_TOKEN o proporciona --token")
    if not math.isfinite(args.latitude) or not -90 <= args.latitude <= 90:
        parser.error("--latitude debe estar entre -90 y 90")
    if not math.isfinite(args.longitude) or not -180 <= args.longitude <= 180:
        parser.error("--longitude debe estar entre -180 y 180")
    if args.events < 1:
        parser.error("--events debe ser al menos 1")
    if not math.isfinite(args.interval_seconds) or args.interval_seconds < 0:
        parser.error("--interval-seconds debe ser un número no negativo")
    if not math.isfinite(args.step_meters) or args.step_meters <= 0:
        parser.error("--step-meters debe ser mayor que cero")
    if not math.isfinite(args.bearing_degrees):
        parser.error("--bearing-degrees debe ser finito")
    if not math.isfinite(args.speed_kmh) or args.speed_kmh < 0:
        parser.error("--speed-kmh debe ser no negativo")
    return args


def location_for_step(args, step):
    bearing = math.radians(args.bearing_degrees)
    distance = args.step_meters * step
    north_meters = distance * math.cos(bearing)
    east_meters = distance * math.sin(bearing)
    latitude = args.latitude + math.degrees(north_meters / EARTH_RADIUS_METERS)
    cosine = math.cos(math.radians(args.latitude))
    if abs(cosine) < 1e-12:
        longitude = args.longitude
    else:
        longitude = args.longitude + math.degrees(east_meters / (EARTH_RADIUS_METERS * cosine))
    if not -90 <= latitude <= 90:
        raise ValueError("La simulación salió del rango de latitudes permitido")
    if not -180 <= longitude <= 180:
        raise ValueError("La simulación salió del rango de longitudes permitido")
    return latitude, longitude


def send_location(args, step):
    latitude, longitude = location_for_step(args, step)
    body = {
        "eventId": str(uuid.uuid4()),
        "latitude": latitude,
        "longitude": longitude,
        "heading": args.bearing_degrees % 360,
        "speedKmh": args.speed_kmh,
        "recordedAt": datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z"),
    }
    request = urllib.request.Request(
        args.base_url.rstrip("/") + "/api/units/me/location",
        data=json.dumps(body).encode("utf-8"),
        headers={"Authorization": "Bearer " + args.token, "Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            result = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        try:
            failure = json.loads(error.read().decode("utf-8"))
            detail = failure.get("code", "API_ERROR")
        except (ValueError, UnicodeDecodeError):
            detail = "API_ERROR"
        raise RuntimeError(f"HTTP {error.code}: {detail}") from None
    return body, result


def main():
    args = parse_args()
    try:
        for step in range(args.events):
            event, result = send_location(args, step)
            print("Ubicación {}/{}: eventId={} currentLocationUpdated={}".format(
                step + 1, args.events, event["eventId"], result["currentLocationUpdated"]))
            if step + 1 < args.events and args.interval_seconds:
                time.sleep(args.interval_seconds)
    except (OSError, RuntimeError, ValueError) as error:
        print("Error al simular ubicación: " + str(error), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
