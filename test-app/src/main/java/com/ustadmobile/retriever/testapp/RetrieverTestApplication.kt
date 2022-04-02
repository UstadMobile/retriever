package com.ustadmobile.retriever.testapp

import android.app.Application
import com.ustadmobile.retriever.Retriever
import com.ustadmobile.retriever.RetrieverBuilderAndroid
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.kodein.di.*

class RetrieverTestApplication: Application(), DIAware {

    override val di: DI by DI.lazy {
        bind<OkHttpClient>() with singleton {
            OkHttpClient.Builder()
                .dispatcher(Dispatcher().also {
                    it.maxRequests = 30
                    it.maxRequestsPerHost = 10
                })
                .build()
        }

        bind<HttpClient>() with singleton {
            HttpClient(OkHttp) {

                install(JsonFeature) {
                    serializer = GsonSerializer()
                }
                install(HttpTimeout)

                val dispatcher = Dispatcher()
                dispatcher.maxRequests = 30
                dispatcher.maxRequestsPerHost = 10

                engine {
                    preconfigured = instance()
                }

            }
        }

        bind<Json>() with singleton {
            Json {
                encodeDefaults = true
            }
        }

        bind<Retriever>() with singleton {
            RetrieverBuilderAndroid
                .builder(applicationContext, "RetrieveDemo", di.direct.instance(),
                    di.direct.instance(), di.direct.instance())
                .build()
        }
    }

    override fun onCreate() {
        super.onCreate()
        Napier.base(DebugAntilog())
    }




}