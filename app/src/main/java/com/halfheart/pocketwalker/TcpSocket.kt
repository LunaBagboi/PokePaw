package com.yourpackage

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

class TcpSocket {
    companion object {
        private const val TAG = "TcpSocket"
        private const val BUFFER_SIZE = 4096
    }

    private var onConnectCallback: (() -> Unit)? = null
    private var onCloseCallback: (() -> Unit)? = null
    private var onDataCallback: ((ByteArray) -> Unit)? = null

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: BufferedInputStream? = null

    private val isConnected = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)
    private val shouldStop = AtomicBoolean(false)

    private var host: String = ""
    private var port: Int = 0

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var readingJob: Job? = null
    private var connectionJob: Job? = null

    fun setOnConnect(callback: () -> Unit) {
        onConnectCallback = callback
    }

    fun setOnClose(callback: () -> Unit) {
        onCloseCallback = callback
    }

    fun setOnData(callback: (ByteArray) -> Unit) {
        onDataCallback = callback
    }

    fun connect(hostname: String, portNumber: Int) {
        // Cancel any existing connection attempt
        connectionJob?.cancel()

        if (isConnected.get()) {
            disconnect()
        }

        // Prevent multiple simultaneous connection attempts
        if (!isConnecting.compareAndSet(false, true)) {
            Log.d(TAG, "Connection already in progress, ignoring request")
            return
        }

        this.host = hostname
        this.port = portNumber
        shouldStop.set(false)

        connectionJob = scope.launch {
            try {
                Log.i(TAG, "Connecting to $hostname:$portNumber")

                val newSocket = Socket(hostname, portNumber).apply {
                    keepAlive = true
                    tcpNoDelay = true
                    soTimeout = 10000
                }

                socket = newSocket
                outputStream = newSocket.getOutputStream()
                inputStream = BufferedInputStream(newSocket.getInputStream())

                isConnected.set(true)
                isConnecting.set(false)

                withContext(Dispatchers.Main) {
                    onConnectCallback?.invoke()
                }

                Log.i(TAG, "Connected successfully")
                startReading()

            } catch (e: IOException) {
                Log.e(TAG, "Connection failed: ${e.message}")
                isConnecting.set(false)
                cleanup()
                withContext(Dispatchers.Main) {
                    onCloseCallback?.invoke()
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Connection attempt cancelled")
                isConnecting.set(false)
                cleanup()
            }
        }
    }

    private suspend fun startReading() {
        readingJob = scope.launch {
            val buffer = ByteArray(BUFFER_SIZE)

            while (isConnected.get() && !shouldStop.get()) {
                try {
                    val inputStream = this@TcpSocket.inputStream
                    if (inputStream == null) {
                        break
                    }

                    val available = inputStream.available()
                    if (available > 0) {
                        val bytesRead = inputStream.read(buffer, 0, minOf(available, BUFFER_SIZE))
                        if (bytesRead > 0) {
                            val data = buffer.copyOf(bytesRead)
                            withContext(Dispatchers.Main) {
                                onDataCallback?.invoke(data)
                            }
                        } else if (bytesRead == -1) {
                            // End of stream
                            break
                        }
                    } else {
                        // No data available, sleep briefly to avoid busy waiting
                        delay(10)
                    }
                } catch (e: SocketException) {
                    Log.w(TAG, "Socket exception during read: ${e.message}")
                    break
                } catch (e: IOException) {
                    Log.e(TAG, "IO error during read: ${e.message}")
                    break
                }
            }

            if (isConnected.get()) {
                disconnect()
            }
        }
    }

    fun send(data: ByteArray) {
        if (!isConnected.get()) {
            Log.w(TAG, "Cannot send data - not connected")
            return
        }

        scope.launch {
            try {
                outputStream?.let { stream ->
                    stream.write(data)
                    stream.flush()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error sending data: ${e.message}")
                disconnect()
            }
        }
    }

    fun isConnected(): Boolean {
        return isConnected.get()
    }

    fun isConnecting(): Boolean {
        return isConnecting.get()
    }

    fun reconnect() {
        // Only reconnect if not already connected or connecting
        if (!isConnected.get() && !isConnecting.get() && host.isNotEmpty() && port > 0) {
            Log.i(TAG, "Attempting to reconnect...")
            connect(host, port)
        } else if (isConnected.get()) {
            Log.d(TAG, "Already connected, skipping reconnect")
        } else if (isConnecting.get()) {
            Log.d(TAG, "Connection in progress, skipping reconnect")
        }
    }

    fun close() {
        shouldStop.set(true)
        connectionJob?.cancel()
        disconnect()
        scope.cancel()
    }

    private fun disconnect() {
        if (!isConnected.compareAndSet(true, false)) {
            return
        }

        Log.i(TAG, "Disconnecting...")
        isConnecting.set(false)

        readingJob?.cancel()
        connectionJob?.cancel()

        cleanup()

        scope.launch(Dispatchers.Main) {
            onCloseCallback?.invoke()
        }
    }

    private fun cleanup() {
        try {
            inputStream?.close()
            inputStream = null
        } catch (e: IOException) {
            Log.w(TAG, "Error closing input stream: ${e.message}")
        }

        try {
            outputStream?.close()
            outputStream = null
        } catch (e: IOException) {
            Log.w(TAG, "Error closing output stream: ${e.message}")
        }

        try {
            socket?.close()
            socket = null
        } catch (e: IOException) {
            Log.w(TAG, "Error closing socket: ${e.message}")
        }
    }
}