package com.example.ecovision

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.expressions.Expression.*
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.util.Locale

private const val MAP_SOURCE = "recycling-source"
private const val MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

private data class RecyclingPoint(
    val id: String, val type: String, val lat: Double, val lon: Double,
    val address: String, val operator: String, val municipality: String,
    val materials: List<String>, val wasteNames: List<String>,
    val info: String, val hours: Map<String, String>
)

private val filters = listOf(
    "all" to "Все", "plastic" to "Пластик", "glass" to "Стекло",
    "paper" to "Бумага", "metal" to "Металл", "deposit" to "Депозит"
)

private fun loadPoints(context: Context): List<RecyclingPoint> {
    val root = JSONObject(context.assets.open("recycling_points.json").bufferedReader().use { it.readText() })
    val array = root.getJSONArray("points")
    return (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        fun strings(key: String): List<String> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).map { a.getString(it) }
        }
        val schedule = o.optJSONObject("hours")
        val hours = if (schedule == null) emptyMap() else
            listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun").associateWith {
                schedule.optString(it, "")
            }
        RecyclingPoint(o.getString("id"), o.optString("type"),
            o.getDouble("lat"), o.getDouble("lon"), o.optString("address"),
            o.optString("operator"), o.optString("municipality"),
            strings("materials"), strings("wasteTypesLv"),
            if (o.isNull("info")) "" else o.optString("info"), hours)
    }
}

private fun pointsGeoJson(points: List<RecyclingPoint>): FeatureCollection {
    val features = points.map { p ->
        Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
            addStringProperty("id", p.id)
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun installPoints(map: MapLibreMap, points: List<RecyclingPoint>) {
    map.getStyle { style ->
        style.getLayer("recycling-clusters")?.let { style.removeLayer(it) }
        style.getLayer("recycling-cluster-count")?.let { style.removeLayer(it) }
        style.getLayer("recycling-single")?.let { style.removeLayer(it) }
        style.getSource(MAP_SOURCE)?.let { style.removeSource(it) }
        style.addSource(GeoJsonSource(MAP_SOURCE, pointsGeoJson(points),
            GeoJsonOptions().withCluster(true).withClusterRadius(48).withClusterMaxZoom(13)))
        style.addLayer(CircleLayer("recycling-clusters", MAP_SOURCE).withProperties(
            circleColor("#377C55"), circleRadius(22f),
            circleStrokeColor("#E6FFF0"), circleStrokeWidth(2f)
        ).withFilter(has("point_count")))
        style.addLayer(SymbolLayer("recycling-cluster-count", MAP_SOURCE).withProperties(
            textField(toString(get("point_count"))), textSize(12f),
            textColor("#FFFFFF"), textIgnorePlacement(true),
            textAllowOverlap(true)
        ).withFilter(has("point_count")))
        style.addLayer(CircleLayer("recycling-single", MAP_SOURCE).withProperties(
            circleColor("#64CE88"), circleRadius(8f),
            circleStrokeColor("#193D2A"), circleStrokeWidth(2f)
        ).withFilter(not(has("point_count"))))
    }
}

private fun nearest(points: List<RecyclingPoint>, location: Location): RecyclingPoint? =
    points.minByOrNull { p ->
        val out = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, p.lat, p.lon, out)
        out[0]
    }

private fun distanceText(point: RecyclingPoint, location: Location?): String? {
    location ?: return null
    val out = FloatArray(1)
    Location.distanceBetween(location.latitude, location.longitude, point.lat, point.lon, out)
    return if (out[0] < 1000f) "${out[0].toInt()} м по прямой"
    else String.format(Locale.getDefault(), "%.1f км по прямой", out[0] / 1000f)
}

private fun openRoute(context: Context, point: RecyclingPoint) {
    val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${point.lat},${point.lon}&travelmode=walking")
    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    catch (_: Exception) { android.widget.Toast.makeText(context, "Не удалось открыть маршрут", android.widget.Toast.LENGTH_LONG).show() }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Suppress("MissingPermission")
private fun requestLocation(context: Context, callback: (Location?) -> Unit) {
    if (!hasLocationPermission(context)) { callback(null); return }
    val service = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val provider = when {
        service.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        service.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> { callback(null); return }
    }
    if (Build.VERSION.SDK_INT >= 30) {
        service.getCurrentLocation(provider, null as CancellationSignal?, ContextCompat.getMainExecutor(context)) { location ->
            callback(location ?: service.getLastKnownLocation(provider))
        }
    } else {
        callback(service.getLastKnownLocation(provider))
    }
}

@Composable
fun RecyclingMapScreen(p: EcoPalette, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var points by remember { mutableStateOf<List<RecyclingPoint>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("all") }
    var selected by remember { mutableStateOf<RecyclingPoint?>(null) }
    var userLocation by remember { mutableStateOf<Location?>(null) }
    var gpsMessage by remember { mutableStateOf<String?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    val currentPoints by rememberUpdatedState(points)
    val mapView = remember(context) { MapLibre.getInstance(context); MapView(context).apply { onCreate(null) } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) {
            requestLocation(context) { loc ->
                userLocation = loc
                gpsMessage = if (loc == null) "Не удалось получить геопозицию. Проверь GPS." else null
                if (loc != null) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 12.5))
            }
        } else gpsMessage = "Геолокация не разрешена. Карту можно использовать вручную."
    }
    fun locate() {
        if (!hasLocationPermission(context)) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        } else requestLocation(context) { loc ->
            userLocation = loc
            gpsMessage = if (loc == null) "Не удалось получить геопозицию. Проверь GPS." else null
            if (loc != null) map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 12.5))
        }
    }

    LaunchedEffect(Unit) {
        try { points = withContext(Dispatchers.IO) { loadPoints(context.applicationContext) } }
        catch (e: Exception) { error = "Не удалось загрузить recycling_points.json: ${e.message}" }
    }
    val visiblePoints = remember(points, selectedFilter) {
        if (selectedFilter == "all") points
        else points.filter { selectedFilter in it.materials }
    }
    LaunchedEffect(visiblePoints, map) {
        map?.let { installPoints(it, visiblePoints) }
    }
    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    LaunchedEffect(mapView) {
        mapView.getMapAsync { ready ->
            map = ready
            ready.cameraPosition = CameraPosition.Builder().target(LatLng(56.9496, 24.1052)).zoom(6.4).build()
            ready.setStyle(MAP_STYLE) {
                installPoints(ready, visiblePoints)
            }
            ready.addOnMapClickListener { latLng ->
                val position = ready.projection.toScreenLocation(latLng)
                val clusters = ready.queryRenderedFeatures(position, "recycling-clusters")
                if (clusters.isNotEmpty()) {
                    ready.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, (ready.cameraPosition.zoom + 2).coerceAtMost(17.0)))
                    true
                } else {
                    val hits = ready.queryRenderedFeatures(position, "recycling-single")
                    val id = hits.firstOrNull()?.getStringProperty("id")
                    if (id != null) {
                        selected = currentPoints.firstOrNull { it.id == id }
                        true
                    } else false
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(p.background)) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().background(p.panel).padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.Close, contentDescription = "Назад", tint = p.text)
                }
                Column(Modifier.weight(1f)) {
                    Text("Карта сортировки", color = p.text, style = MaterialTheme.typography.titleMedium)
                    Text("${visiblePoints.size} пунктов · Латвия", color = p.muted, fontSize = 12.sp)
                }
                IconButton(onClick = { locate() }) {
                    Icon(Icons.Rounded.MyLocation, contentDescription = "Моё местоположение", tint = p.accent)
                }
            }
            Row(Modifier.fillMaxWidth().background(p.panel).horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { (key, name) ->
                    FilterChip(selected = key == selectedFilter, onClick = {
                        selectedFilter = key; selected = null
                    }, label = { Text(name) })
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.background(p.panel).padding(12.dp)) }
            gpsMessage?.let { Text(it, color = p.text, modifier = Modifier.background(p.panel).padding(12.dp)) }
        }
        if (selected != null) {
            val point = selected!!
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .fillMaxWidth().padding(12.dp)
                .background(p.panel, RoundedCornerShape(24.dp))
                .padding(18.dp).heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Пункт сортировки", style = MaterialTheme.typography.titleMedium,
                        color = p.text, modifier = Modifier.weight(1f))
                    IconButton(onClick = { selected = null }) { Icon(Icons.Rounded.Close, "Закрыть", tint = p.text) }
                }
                Text(point.address.ifBlank { point.municipality }, color = p.text)
                distanceText(point, userLocation)?.let { Text(it, color = p.accent) }
                Text("Принимает: " + point.wasteNames.joinToString().ifBlank { point.materials.joinToString() }, color = p.muted, fontSize = 13.sp)
                if (point.operator.isNotBlank()) Text("Оператор: ${point.operator}", color = p.text, fontSize = 13.sp)
                if (point.info.isNotBlank()) Text(point.info, color = p.muted, fontSize = 13.sp)
                val days = listOf("mon" to "Пн", "tue" to "Вт", "wed" to "Ср", "thu" to "Чт",
                    "fri" to "Пт", "sat" to "Сб", "sun" to "Вс")
                days.forEach { (key, label) ->
                    val hours = point.hours[key].orEmpty()
                    if (hours.isNotBlank()) Text("$label: $hours", color = p.muted, fontSize = 12.sp)
                }
                Button(onClick = { openRoute(context, point) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Navigation, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Построить маршрут")
                }
            }
        } else if (points.isNotEmpty()) {
            Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)) {
                if (userLocation != null) {
                    val nearest = nearest(visiblePoints, userLocation!!)
                    if (nearest != null) {
                        Button(onClick = {
                            selected = nearest
                            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(nearest.lat, nearest.lon), 14.0))
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Ближайший пункт · ${distanceText(nearest, userLocation)}")
                        }
                    }
                } else {
                    Surface(shape = RoundedCornerShape(16.dp), color = p.panel) {
                        Text("Нажми на точку или значок геолокации", modifier = Modifier.padding(12.dp), color = p.text)
                    }
                }
            }
        }
    }
}