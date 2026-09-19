package com.shiv.rally.di

import android.content.Context
import com.shiv.rally.BuildConfig
import androidx.room.Room
import com.shiv.rally.data.local.AppDatabase
import com.shiv.rally.data.local.ChannelDao
import com.shiv.rally.data.remote.sports.EspnApi
import com.shiv.rally.data.remote.sports.EspnRepositoryImpl
import com.shiv.rally.data.remote.network.ResilientDns
import com.shiv.rally.data.remote.stalker.StalkerApi
import com.shiv.rally.data.remote.stalker.StalkerIptvRepositoryImpl
import com.shiv.rally.domain.repository.IptvRepository
import com.shiv.rally.domain.repository.SportsRepository
import com.shiv.rally.domain.usecase.MatchEventToStreamUseCase
import com.shiv.rally.domain.usecase.MatcherService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Singleton
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    fun provideIoDispatcher(): kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun safeLogging(level: HttpLoggingInterceptor.Level): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            this.level = if (BuildConfig.DEBUG) level else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }

    @Provides
    @Singleton
    fun provideStalkerApi(
        authInterceptor: com.shiv.rally.data.remote.stalker.AuthInterceptor,
        preferencesManager: com.shiv.rally.data.local.PreferencesManager,
        resilientDns: ResilientDns
    ): StalkerApi {
        val logging = safeLogging(HttpLoggingInterceptor.Level.BASIC)
        
        val cookieJar = object : CookieJar {
            private val cookieStore = ConcurrentHashMap<String, List<Cookie>>()
            
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                val list = (cookieStore[url.host] ?: emptyList()).toMutableList()
                val mac = preferencesManager.macAddress.trim()
                if (mac.isNotEmpty()) {
                    try {
                        list.removeAll { it.name in listOf("mac", "stb_lang", "timezone") }
                        list.add(Cookie.Builder().name("mac").value(mac).domain(url.host).path("/").build())
                        list.add(Cookie.Builder().name("stb_lang").value("en").domain(url.host).path("/").build())
                        list.add(Cookie.Builder().name("timezone").value("GMT").domain(url.host).path("/").build())
                    } catch (e: Exception) {
                        android.util.Log.e("StalkerCookie", "Error building cookies for ${url.host}", e)
                    }
                }
                return list
            }
        }
        
        val client = OkHttpClient.Builder()
            .dns(resilientDns)
            .cookieJar(cookieJar)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(18, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
            
        // Use a placeholder base URL. The AuthInterceptor will override it with the real portal URL.
        return Retrofit.Builder()
            .baseUrl("http://placeholder.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StalkerApi::class.java)
    }

    @Provides
    @Singleton
    fun provideEspnApi(resilientDns: ResilientDns): EspnApi {
        val logging = safeLogging(HttpLoggingInterceptor.Level.BASIC)
        val client = OkHttpClient.Builder()
            .dns(resilientDns)
            .addInterceptor(logging)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://site.api.espn.com/apis/site/v2/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EspnApi::class.java)
    }

    @Provides
    @Singleton
    fun provideStremioApi(resilientDns: ResilientDns): com.shiv.rally.data.remote.stremio.StremioApi {
        val client = OkHttpClient.Builder()
            .dns(resilientDns)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
        return Retrofit.Builder()
            .baseUrl("https://sports.highfly.dev/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.shiv.rally.data.remote.stremio.StremioApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "rally.db"
        ).addMigrations(object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE channels ADD COLUMN supportsCatchUp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE channels ADD COLUMN archiveDurationHours INTEGER")
            }
        }).build()
    }

    @Provides
    fun provideChannelDao(database: AppDatabase): ChannelDao {
        return database.channelDao()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSportsRepository(
        impl: EspnRepositoryImpl
    ): SportsRepository

    @Binds
    @Singleton
    abstract fun bindIptvRepository(
        impl: StalkerIptvRepositoryImpl
    ): IptvRepository

    @Binds
    @Singleton
    abstract fun bindStremioRepository(
        impl: com.shiv.rally.data.remote.stremio.StremioRepositoryImpl
    ): com.shiv.rally.domain.repository.StremioRepository

    @Binds
    @Singleton
    abstract fun bindMatcherService(
        impl: MatchEventToStreamUseCase
    ): MatcherService

}
