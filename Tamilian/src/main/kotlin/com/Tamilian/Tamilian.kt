package com.Tamilian

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class Tamilian : TmdbProvider() {
    override var name = "Tamilian"
    override val hasMainPage = true
    override var lang = "ta"
    override val instantLinkLoading = true
    override val useMetaLoadResponse = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.Movie,
    )

    companion object {
        const val HOST = "https://embedojo.net"
        const val TMDB_API_KEY = "s" // You'll need to get this from TMDB
        const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
    }

    override val mainPage = mainPageOf(
        "$TMDB_BASE_URL/discover/movie?api_key=$TMDB_API_KEY&with_original_language=ta&sort_by=popularity.desc&page=" to "Popular Tamil Movies",
        "$TMDB_BASE_URL/movie/now_playing?api_key=$TMDB_API_KEY&language=ta&region=IN&page=" to "Now Playing",
        "$TMDB_BASE_URL/discover/movie?api_key=$TMDB_API_KEY&with_original_language=ta&sort_by=release_date.desc&page=" to "Latest Tamil Movies",
        "$TMDB_BASE_URL/discover/movie?api_key=$TMDB_API_KEY&with_original_language=ta&vote_average.gte=7&sort_by=vote_average.desc&page=" to "Top Rated Tamil Movies",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page
        val response = app.get(url).parsed<TmdbResponse>()
        
        val movies = response.results?.mapNotNull { movie ->
            movie.toSearchResponse()
        } ?: emptyList()

        return newHomePageResponse(request.name, movies, hasNext = page < (response.totalPages ?: 1))
    }

    private fun TmdbMovie.toSearchResponse(): MovieSearchResponse? {
        return newMovieSearchResponse(
            name = this.title ?: this.originalTitle ?: return null,
            url = TmdbLink(
                tmdbID = this.id,
                imdbID = null,
                movieName = this.title,
                year = this.releaseDate?.substringBefore("-")?.toIntOrNull()
            ).toString(),
            type = TvType.Movie,
        ) {
            this.posterUrl = if (this@toSearchResponse.posterPath != null) {
                "https://image.tmdb.org/t/p/w500${this@toSearchResponse.posterPath}"
            } else null
            this.year = this@toSearchResponse.releaseDate?.substringBefore("-")?.toIntOrNull()
            this.quality = SearchQuality.HD
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$TMDB_BASE_URL/search/movie?api_key=$TMDB_API_KEY&query=${query}&language=ta"
        val response = app.get(searchUrl).parsed<TmdbResponse>()
        
        return response.results?.mapNotNull { movie ->
            // Filter for Tamil movies only
            if (movie.originalLanguage == "ta") {
                movie.toSearchResponse()
            } else null
        } ?: emptyList()
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val mediaData = AppUtils.parseJson<TmdbLink>(data).toLinkData()
        val script = app.get("$HOST/tamil/tmdb/${mediaData.tmdbId}")
            .document.selectFirst("script:containsData(function(p,a,c,k,e,d))")
            ?.data()?.let { getAndUnpack(it) }

        val token = script?.substringAfter("FirePlayer(\"")?.substringBefore("\",")
        val m3u8 = app.post("$HOST/player/index.php?data=$token&do=getVideo", headers = mapOf("X-Requested-With" to "XMLHttpRequest"))
            .parsedSafe<VideoData>()
        val headers = mapOf("Origin" to "https://embedojo.net")
        
        m3u8?.let {
            safeApiCall {
                callback.invoke(
                    newExtractorLink(
                        name,
                        name,
                        url = it.videoSource,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.P1080.value
                        this.headers = headers
                    }
                )
            }
        }
        return true
    }

    private fun TmdbLink.toLinkData(): LinkData {
        return LinkData(
            imdbId = imdbID,
            tmdbId = tmdbID,
            title = movieName,
            season = season,
            episode = episode
        )
    }

    // TMDB API Response Models
    data class TmdbResponse(
        @JsonProperty("page") val page: Int?,
        @JsonProperty("results") val results: List<TmdbMovie>?,
        @JsonProperty("total_pages") val totalPages: Int?,
        @JsonProperty("total_results") val totalResults: Int?
    )

    data class TmdbMovie(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("original_title") val originalTitle: String?,
        @JsonProperty("original_language") val originalLanguage: String?,
        @JsonProperty("overview") val overview: String?,
        @JsonProperty("poster_path") val posterPath: String?,
        @JsonProperty("backdrop_path") val backdropPath: String?,
        @JsonProperty("release_date") val releaseDate: String?,
        @JsonProperty("vote_average") val voteAverage: Double?,
        @JsonProperty("vote_count") val voteCount: Int?,
        @JsonProperty("popularity") val popularity: Double?,
        @JsonProperty("adult") val adult: Boolean?,
        @JsonProperty("genre_ids") val genreIds: List<Int>?
    )

    data class LinkData(
        @JsonProperty("simklId") val simklId: Int? = null,
        @JsonProperty("traktId") val traktId: Int? = null,
        @JsonProperty("imdbId") val imdbId: String? = null,
        @JsonProperty("tmdbId") val tmdbId: Int? = null,
        @JsonProperty("tvdbId") val tvdbId: Int? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
        @JsonProperty("aniId") val aniId: String? = null,
        @JsonProperty("malId") val malId: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("orgTitle") val orgTitle: String? = null,
        @JsonProperty("isAnime") val isAnime: Boolean = false,
        @JsonProperty("airedYear") val airedYear: Int? = null,
        @JsonProperty("lastSeason") val lastSeason: Int? = null,
        @JsonProperty("epsTitle") val epsTitle: String? = null,
        @JsonProperty("jpTitle") val jpTitle: String? = null,
        @JsonProperty("date") val date: String? = null,
        @JsonProperty("airedDate") val airedDate: String? = null,
        @JsonProperty("isAsian") val isAsian: Boolean = false,
        @JsonProperty("isBollywood") val isBollywood: Boolean = false,
        @JsonProperty("isCartoon") val isCartoon: Boolean = false,
    )

    data class VideoData(
        val hls: Boolean,
        val videoImage: String,
        val videoSource: String,
        val securedLink: String,
        val downloadLinks: List<Any?>,
        val attachmentLinks: List<Any?>,
        val ck: String,
    )
}
