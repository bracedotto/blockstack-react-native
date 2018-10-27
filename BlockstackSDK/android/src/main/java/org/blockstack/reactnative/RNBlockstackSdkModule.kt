package org.blockstack.reactnative

import android.util.Base64
import android.util.Log
import com.facebook.react.bridge.*
import org.blockstack.android.sdk.*
import java.net.URI

class RNBlockstackSdkModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    override fun getName() = "RNBlockstackSdk"

    override fun getConstants(): MutableMap<String, Any> {
        val constants = HashMap<String, Any>()
        return constants;
    }

    private lateinit var session: BlockstackSession

    @ReactMethod
    fun createSession(configArg: ReadableMap, promise: Promise) {
        val activity = getReactApplicationContext().currentActivity
        if (activity != null) {
            val scopes = configArg.getArray("scopes")
                    .toArrayList().map {
                        Scope.valueOf((it as String)
                                .split("_").joinToString("") { it.capitalize() })
                    }
                    .toTypedArray()

            if (!configArg.hasKey("appDomain")) {
                throw IllegalArgumentException("'appDomain' needed in config object")
            }
            val appDomain = configArg.getString("appDomain")
            val manifestPath = if (configArg.hasKey("manifestUrl")) {
                configArg.getString("manifestUrl")
            } else {
                "/manifest.json"
            }

            val redirectPath = if (configArg.hasKey("redirectUrl")) {
                configArg.getString("redirectUrl")
            } else {
                "/redirect"
            }
            val config = BlockstackConfig(URI(appDomain), redirectPath, manifestPath, scopes)


            Log.d("BlockstackNativeModule", "create session" + Thread.currentThread().name)
            session = BlockstackSession(activity, config)
            Log.d("BlockstackNativeModule", "created session")
            val map = Arguments.createMap()
            map.putBoolean("loaded", true)
            promise.resolve(map)
            currentSession = session

        } else {
            promise.reject(IllegalStateException("must be called from an Activity that implements ConfigProvider"))
        }
    }

    @ReactMethod
    fun isUserSignedIn(promise: Promise) {
        if (session.loaded) {
            val map = Arguments.createMap()
            map.putBoolean("signedIn", session.isUserSignedIn())
            promise.resolve(map)
        }
    }

    @ReactMethod
    fun signIn(promise: Promise) {
        if (session.loaded) {
            RNBlockstackSdkModule.currentSignInPromise = promise
            session.redirectUserToSignIn {
                // never called
            }
            session.releaseThreadLock()
        } else {
            promise.reject("NOT_LOADED", "Session not loaded")
        }
    }

    @ReactMethod
    fun signUserOut(promise: Promise) {
        if (session.loaded) {
            session.signUserOut()
            val map = Arguments.createMap()
            map.putBoolean("signedOut", true)
            promise.resolve(map)
        } else {
            promise.reject("NOT_LOADED", "Session not loaded")
        }
    }

    @ReactMethod
    fun loadUserData(promise: Promise) {
        if(session.loaded) {
            val decentralizedID = session.loadUserData()?.decentralizedID
            val map = Arguments.createMap()
            map.putString("decentralizedID", decentralizedID)
            promise.resolve(map)
        } else {
            promise.reject("NOT_LOADED", "Session not loaded")
        }
    }

    @ReactMethod
    fun putFile(path: String, content: String, optionsArg: ReadableMap, promise: Promise) {
        session.aquireThreadLock()
        if (canUseBlockstack()) {
            val options = PutFileOptions(optionsArg.getBoolean("encrypt"))
            session.putFile(path, content, options) {
                Log.d("RNBlockstackSdkModuel", "putFile result")
                if (it.hasValue) {
                    val map = Arguments.createMap()
                    map.putString("fileUrl", it.value)
                    promise.resolve(map)
                } else {
                    promise.reject("0", it.error)
                }
                try {
                    session.releaseThreadLock()
                } catch (e:Exception) {
                    Log.d("RNBlockstackSdkModuel", e.toString(), e)
                }
            }
        }
    }

    @ReactMethod
    fun getFile(path: String, optionsArg: ReadableMap, promise: Promise) {
        session.aquireThreadLock()
        if (canUseBlockstack()) {
            val options = GetFileOptions(optionsArg.getBoolean("decrypt"))
            session.getFile(path, options) {
                if (it.hasValue) {
                    val map = Arguments.createMap()
                    if (it.value is String) {
                        map.putString("fileContents", it.value as String)
                    } else {
                        map.putString("fileContentsEncoded", Base64.encodeToString(it.value as ByteArray, Base64.NO_WRAP))
                    }
                    promise.resolve(map)
                } else {
                    promise.reject("0", it.error)
                }
            }

        }
    }

    private fun canUseBlockstack() = session.loaded && reactApplicationContext.currentActivity != null

    companion object {
        // TODO only store transitKey and the likes in this static variable
        @JvmStatic
        var currentSession: BlockstackSession? = null
        @JvmStatic
        var currentSignInPromise: Promise? = null
    }
}