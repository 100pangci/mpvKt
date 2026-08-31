package live.mehiz.mpvkt.player

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Compatibility layer exposing the legacy global-static mpv API on top of the
 * instance-based [MPV] class (mpv-android-lib >= 0.1.10). The instance is owned
 * by [BaseMPVView][`is`.xyz.mpv.BaseMPVView] and attached via [attach].
 */
@Suppress("TooManyFunctions")
object MPVLib {

  val mpvFormat = MPV.mpvFormat
  val mpvEventId = MPV.mpvEvent
  val mpvLogLevel = MPV.mpvLogLevel

  private var mpv: MPV? = null
  private val mpvLock = Any()
  private val adapter = object : MPV.EventObserver {
    override fun eventProperty(property: String) {
      synchronized(observers) { for (o in observers) o.eventProperty(property) }
    }

    override fun eventProperty(property: String, value: Long) {
      propLong.emit(property, value)
      propInt.emit(property, value.toInt())
      synchronized(observers) { for (o in observers) o.eventProperty(property, value) }
    }

    override fun eventProperty(property: String, value: Boolean) {
      propBoolean.emit(property, value)
      synchronized(observers) { for (o in observers) o.eventProperty(property, value) }
    }

    override fun eventProperty(property: String, value: String) {
      propString.emit(property, value)
      synchronized(observers) { for (o in observers) o.eventProperty(property, value) }
    }

    override fun eventProperty(property: String, value: Double) {
      propDouble.emit(property, value)
      propFloat.emit(property, value.toFloat())
      synchronized(observers) { for (o in observers) o.eventProperty(property, value) }
    }

    override fun eventProperty(property: String, value: MPVNode) {
      propNode.emit(property, value)
      synchronized(observers) { for (o in observers) o.eventProperty(property, value) }
    }

    override fun event(eventId: Int, data: MPVNode) {
      synchronized(observers) { for (o in observers) o.event(eventId) }
    }
  }

  fun attach(instance: MPV) = synchronized(mpvLock) {
    mpv = instance
    instance.addObserver(adapter)
    instance.addLogObserver(logAdapter)
    propBoolean.reobserveAll()
    propString.reobserveAll()
    propDouble.reobserveAll()
    propFloat.reobserveAll()
    propLong.reobserveAll()
    propInt.reobserveAll()
    propNode.reobserveAll()
  }

  fun create(appctx: Context?) = synchronized(mpvLock) { mpv?.create(appctx!!) }
  fun init() = synchronized(mpvLock) { mpv?.init() }
  fun destroy() = synchronized(mpvLock) {
    mpv?.let {
      it.removeObserver(adapter)
      it.removeLogObserver(logAdapter)
      it.destroy()
    }
    mpv = null
  }

  fun attachSurface(surface: Surface) = withMpv { it.attachSurface(surface) }
  fun detachSurface() = withMpv { it.detachSurface() }

  private fun <T> withMpv(block: (MPV) -> T?): T? = synchronized(mpvLock) {
    mpv?.takeIf { it.isInitialized }?.let(block)
  }

  fun command(vararg cmd: String) = withMpv { it.command(*cmd) }
  fun commandNode(vararg cmd: String): MPVNode? = withMpv { it.commandNode(*cmd) }

  fun setOptionString(name: String, value: String): Int =
    withMpv { it.setOptionString(name, value) } ?: -1

  fun grabThumbnail(dimension: Int): Bitmap? = withMpv { it.grabThumbnail(dimension) }

  fun getPropertyInt(property: String): Int? = withMpv { it.getPropertyInt(property) }
  fun setPropertyInt(property: String, value: Int) = withMpv { it.setPropertyInt(property, value) }
  fun getPropertyDouble(property: String): Double? = withMpv { it.getPropertyDouble(property) }
  fun setPropertyDouble(property: String, value: Double) = withMpv {
    it.setPropertyDouble(property, value)
  }

  fun getPropertyBoolean(property: String): Boolean? = withMpv { it.getPropertyBoolean(property) }

  fun setPropertyBoolean(property: String, value: Boolean) = withMpv {
    it.setPropertyBoolean(property, value)
  }

  fun getPropertyString(property: String): String? = withMpv { it.getPropertyString(property) }
  fun setPropertyString(property: String, value: String) = withMpv {
    it.setPropertyString(property, value)
  }

  fun getPropertyNode(property: String): MPVNode? = withMpv { it.getPropertyNode(property) }
  fun setPropertyNode(property: String, node: MPVNode) = withMpv {
    it.setPropertyNode(property, node)
  }

  fun getPropertyFloat(property: String) = getPropertyDouble(property)?.toFloat()
  fun setPropertyFloat(property: String, value: Float) =
    setPropertyDouble(property, value.toDouble())

  fun getPropertyLong(property: String) = getPropertyInt(property)?.toLong()
  fun setPropertyLong(property: String, value: Long) = setPropertyInt(property, value.toInt())

  fun observeProperty(property: String, format: Int) = withMpv {
    it.observeProperty(property, format)
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  class Property<T> internal constructor(
    val type: Int,
    val getProperty: (String) -> T?,
  ) {
    internal val flow = MutableSharedFlow<Pair<String, T>>()
    internal val map = mutableMapOf<String, StateFlow<T?>>()

    operator fun get(property: String): StateFlow<T?> = map.getOrPut(property) {
      observeProperty(property, type)
      flow.filter { it.first == property }
        .map { it.second }
        .stateIn(
          scope,
          SharingStarted.Lazily,
          withMpv { getProperty(property) },
        )
    }

    operator fun set(property: String, value: T) {
      when (type) {
        mpvFormat.MPV_FORMAT_INT64 -> setPropertyInt(property, value as Int)
        mpvFormat.MPV_FORMAT_FLAG -> setPropertyBoolean(property, value as Boolean)
        mpvFormat.MPV_FORMAT_STRING -> setPropertyString(property, value as String)
        mpvFormat.MPV_FORMAT_DOUBLE -> setPropertyDouble(property, value as Double)
        mpvFormat.MPV_FORMAT_NODE,
        mpvFormat.MPV_FORMAT_NODE_ARRAY,
        mpvFormat.MPV_FORMAT_NODE_MAP,
        -> setPropertyNode(property, value as MPVNode)
        else -> throw IllegalArgumentException("Unsupported property type")
      }
    }

    internal fun emit(property: String, value: T) {
      scope.launch { flow.emit(Pair(property, value)) }
    }

    internal fun reobserveAll() {
      map.keys.forEach { observeProperty(it, type) }
    }
  }

  val propInt = Property(mpvFormat.MPV_FORMAT_INT64, ::getPropertyInt)
  val propBoolean = Property(mpvFormat.MPV_FORMAT_FLAG, ::getPropertyBoolean)
  val propString = Property(mpvFormat.MPV_FORMAT_STRING, ::getPropertyString)
  val propDouble = Property(mpvFormat.MPV_FORMAT_DOUBLE, ::getPropertyDouble)
  val propNode = Property(mpvFormat.MPV_FORMAT_NODE, ::getPropertyNode)
  val propLong = Property(mpvFormat.MPV_FORMAT_INT64) { getPropertyInt(it)?.toLong() }
  val propFloat = Property(mpvFormat.MPV_FORMAT_DOUBLE) { getPropertyDouble(it)?.toFloat() }

  fun eventFlow(property: String): Flow<Unit> {
    observeProperty(property, mpvFormat.MPV_FORMAT_NONE)
    return eventPropertyFlow.filter { it == property }.map { }
  }

  fun eventFlow(eventId: Int): Flow<Unit> = eventFlowInternal.filter { it == eventId }.map { }

  private val eventFlowInternal = MutableSharedFlow<Int>()
  private val eventPropertyFlow = MutableSharedFlow<String>()

  private val observers: MutableList<EventObserver> = ArrayList()

  fun addObserver(o: EventObserver) {
    synchronized(observers) { observers.add(o) }
  }

  fun removeObserver(o: EventObserver) {
    synchronized(observers) { observers.remove(o) }
  }

  interface EventObserver {
    fun eventProperty(property: String)
    fun eventProperty(property: String, value: Long)
    fun eventProperty(property: String, value: Boolean)
    fun eventProperty(property: String, value: String)
    fun eventProperty(property: String, value: Double)
    fun eventProperty(property: String, value: MPVNode)
    fun event(eventId: Int)
  }

  interface LogObserver {
    fun logMessage(prefix: String, level: Int, text: String)
  }

  private val logAdapter = object : MPV.LogObserver {
    override fun logMessage(prefix: String, level: Int, text: String) {
      synchronized(log_observers) { for (o in log_observers) o.logMessage(prefix, level, text) }
      scope.launch { logFlow.emit(Triple(prefix, level, text)) }
    }
  }

  private val log_observers: MutableList<LogObserver> = ArrayList()
  val logFlow = MutableSharedFlow<Triple<String, Int, String>>()

  fun addLogObserver(o: LogObserver) {
    synchronized(log_observers) { log_observers.add(o) }
  }

  fun removeLogObserver(o: LogObserver) {
    synchronized(log_observers) { log_observers.remove(o) }
  }
}
