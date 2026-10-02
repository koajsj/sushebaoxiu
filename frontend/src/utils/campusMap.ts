import type { MapMarker, ProjectedMarker } from '../types/dispatch'
import { validLocation } from './map'

/** Illustration coordinates only. These are fictional landmarks, not database buildings. */
export const campusLandmarks = [
  { id: 'sports', name: '运动场', x: 240, y: 165 },
  { id: 'library', name: '校园图书馆', x: 490, y: 170 },
  { id: 'teaching-a', name: '教学楼A栋', x: 665, y: 175 },
  { id: 'teaching-b', name: '教学楼B栋', x: 830, y: 170 },
  { id: 'dorm-1', name: '学生宿舍1号楼', x: 225, y: 315 },
  { id: 'dorm-2', name: '学生宿舍2号楼', x: 170, y: 415 },
  { id: 'dorm-3', name: '学生宿舍3号楼', x: 355, y: 415 },
  { id: 'canteen', name: '第一食堂', x: 660, y: 350 },
  { id: 'service', name: '维修服务站', x: 820, y: 390 },
  { id: 'gate', name: '校园南门', x: 500, y: 510 },
] as const
type Landmark = typeof campusLandmarks[number]

function landmarkForName(name: string): Landmark | undefined {
  const normalized = name.replace(/\s/g, '')
  return campusLandmarks.find(landmark => normalized.includes(landmark.name))
}

export function campusLandmarkForMarker(marker: MapMarker, locations: MapMarker[]): Landmark | undefined {
  if (marker.kind === 'building') return landmarkForName(marker.building?.name || marker.title)
  if (marker.order) {
    const named = landmarkForName(marker.order.address)
    if (named) return named
  }
  if (!validLocation(marker.longitude, marker.latitude)) return undefined
  // Existing static locations are only used to associate a marker with a known building.
  // Never convert geography into pixel coordinates or invent a position for an unknown site.
  const candidates = locations.filter(location => location.kind === 'building'
    && validLocation(location.longitude, location.latitude)
    && landmarkForName(location.building?.name || location.title))
  const distance = (location: MapMarker) => {
    const dx = (location.longitude! - marker.longitude!) * Math.cos(marker.latitude! * Math.PI / 180)
    return Math.hypot(dx, location.latitude! - marker.latitude!) * 111_320
  }
  const closest = candidates.reduce<MapMarker | undefined>((best, location) =>
    !best || distance(location) < distance(best) ? location : best, undefined)
  const limit = marker.kind === 'worker' ? 200 : 10
  return closest && distance(closest) <= limit ? landmarkForName(closest.building?.name || closest.title) : undefined
}

export function projectCampusMarkers(markers: MapMarker[], locations: MapMarker[] = markers): ProjectedMarker[] {
  const groups = new Map<string, ProjectedMarker[]>()
  for (const marker of markers) {
    const landmark = campusLandmarkForMarker(marker, locations)
    if (!landmark) continue
    const group = groups.get(landmark.id) || []
    group.push({ ...marker, x: landmark.x, y: landmark.y, anchorX: landmark.x, anchorY: landmark.y })
    groups.set(landmark.id, group)
  }
  for (const group of groups.values()) {
    // A building remains on its footprint; tasks fan out below its name.
    const tasks = group.filter(marker => marker.kind !== 'building').sort((a, b) => a.key.localeCompare(b.key))
    const columns = Math.min(6, tasks.length)
    const rows = Math.ceil(tasks.length / Math.max(1, columns))
    const spacing = Math.min(38, 520 / Math.max(1, rows - 1))
    const startY = Math.min(group[0]!.anchorY + 74, 610 - (rows - 1) * spacing)
    tasks.forEach((marker, index) => {
      marker.x = marker.anchorX + (index % columns - (columns - 1) / 2) * 38
      marker.y = startY + Math.floor(index / columns) * spacing
    })
  }
  return [...groups.values()].flat()
}
