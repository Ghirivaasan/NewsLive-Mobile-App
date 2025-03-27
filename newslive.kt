// NewsApiService.kt
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface NewsApiService {
    @GET("top-headlines")
    suspend fun getTopHeadlines(
        @Query("country") country: String = "us",
        @Query("category") category: String? = null,
        @Query("q") query: String? = null
    ): NewsResponse

    @GET("everything")
    suspend fun searchArticles(
        @Query("q") query: String,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): NewsResponse

    companion object {
        private const val BASE_URL = "https://newsapi.org/v2/"

        fun create(): NewsApiService {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            return retrofit.create(NewsApiService::class.java)
        }
    }
}

// NewsRepository.kt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NewsRepository(private val newsApiService: NewsApiService, private val newsDao: NewsDao) {
    suspend fun getTopHeadlines(
        country: String = "us",
        category: String? = null,
        query: String? = null
    ): List<Article> {
        return withContext(Dispatchers.IO) {
            val response = newsApiService.getTopHeadlines(country, category, query)
            response.articles
        }
    }

    suspend fun searchArticles(query: String, from: String? = null, to: String? = null): List<Article> {
        return withContext(Dispatchers.IO) {
            val response = newsApiService.searchArticles(query, from, to)
            response.articles
        }
    }

    suspend fun getFavoriteArticles(): List<Article> {
        return withContext(Dispatchers.IO) {
            newsDao.getAllFavoriteArticles()
        }
    }

    suspend fun toggleFavoriteArticle(article: Article) {
        withContext(Dispatchers.IO) {
            if (newsDao.isFavorite(article.url)) {
                newsDao.removeFavoriteArticle(article)
            } else {
                newsDao.addFavoriteArticle(article)
            }
        }
    }
}

// NewsViewModel.kt
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class NewsViewModel(private val newsRepository: NewsRepository) : ViewModel() {
    private val _articles = MutableLiveData<List<Article>>()
    val articles: LiveData<List<Article>> = _articles

    private val _favoriteArticles = MutableLiveData<List<Article>>()
    val favoriteArticles: LiveData<List<Article>> = _favoriteArticles

    fun getTopHeadlines(
        country: String = "us",
        category: String? = null,
        query: String? = null
    ) {
        viewModelScope.launch {
            val articles = newsRepository.getTopHeadlines(country, category, query)
            _articles.value = articles
        }
    }

    fun searchArticles(query: String, from: String? = null, to: String? = null) {
        viewModelScope.launch {
            val articles = newsRepository.searchArticles(query, from, to)
            _articles.value = articles
        }
    }

    fun getFavoriteArticles() {
        viewModelScope.launch {
            val favoriteArticles = newsRepository.getFavoriteArticles()
            _favoriteArticles.value = favoriteArticles
        }
    }

    fun toggleFavoriteArticle(article: Article) {
        viewModelScope.launch {
            newsRepository.toggleFavoriteArticle(article)
        }
    }
}

// NewsFragment.kt
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.newsapp.databinding.FragmentNewsBinding

class NewsFragment : Fragment() {
    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!

    private val newsViewModel: NewsViewModel by lazy {
        ViewModelProvider(this, NewsViewModelFactory(NewsRepository(NewsApiService.create(), newsDao)))
            .get(NewsViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val newsAdapter = NewsAdapter(::toggleFavoriteArticle)
        binding.newsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = newsAdapter
        }

        newsViewModel.articles.observe(viewLifecycleOwner) { articles ->
            newsAdapter.submitList(articles)
        }

        newsViewModel.getTopHeadlines()
    }

    private fun toggleFavoriteArticle(article: Article) {
        newsViewModel.toggleFavoriteArticle(article)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// NewsAdapter.kt
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.newsapp.R
import com.example.newsapp.databinding.ItemNewsBinding

class NewsAdapter(private val toggleFavoriteArticle: (Article) -> Unit) : ListAdapter<Article, NewsAdapter.NewsViewHolder>(DiffCallback) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val binding = ItemNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NewsViewHolder(binding, toggleFavoriteArticle)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        val article = getItem(position)
        holder.bind(article)
    }

    class NewsViewHolder(
        private val binding: ItemNewsBinding,
        private val toggleFavoriteArticle: (Article) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        private var currentArticle: Article? = null

        init {
            binding.favoriteButton.setOnClickListener {
                currentArticle?.let { article ->
                    toggleFavoriteArticle(article)
                }
            }
        }

        fun bind(article: Article) {
            currentArticle = article
            binding.titleTextView.text = article.title
            binding.descriptionTextView.text = article.description
            Glide.with(binding.root)
                .load(article.urlToImage)
                .into(binding.imageView)
            binding.favoriteButton.setImageResource(
                if (article.isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
            )
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<Article>() {
        override fun areItemsTheSame(oldItem: Article, newItem: Article): Boolean {
            return oldItem.url == newItem.url
        }

        override fun areContentsTheSame(oldItem: Article, newItem: Article): Boolean {
            return oldItem == newItem
        }
    }
}



// NewsDao.kt
@Dao
interface NewsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArticles(articles: List<Article>)

    @Query("SELECT * FROM articles WHERE isFavorite = 1")
    fun getAllFavoriteArticles(): List<Article>

    @Query("SELECT * FROM articles WHERE url = :url")
    fun getArticleByUrl(url: String): Article?

    @Update
    suspend fun updateArticle(article: Article)
}

// Article.kt
@Entity(tableName = "articles")
data class Article(
    @PrimaryKey
    val url: String,
    val title: String,
    val description: String,
    val urlToImage: String,
    var isFavorite: Boolean = false
)

// NewsRepository.kt
class NewsRepository(
    private val newsApiService: NewsApiService,
    private val newsDao: NewsDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    suspend fun getTopHeadlines(
        country: String = "us",
        category: String? = null,
        query: String? = null
    ): List<Article> {
        return withContext(Dispatchers.IO) {
            val articles = newsDao.getAllArticles()
            if (articles.isNotEmpty()) {
                articles
            } else {
                val response = newsApiService.getTopHeadlines(country, category, query)
                newsDao.insertArticles(response.articles)
                response.articles
            }
        }
    }

    suspend fun searchArticles(query: String, from: String? = null, to: String? = null): List<Article> {
        return withContext(Dispatchers.IO) {
            val response = newsApiService.searchArticles(query, from, to)
            newsDao.insertArticles(response.articles)
            response.articles
        }
    }

    suspend fun getFavoriteArticles(): List<Article> {
        return withContext(Dispatchers.IO) {
            newsDao.getAllFavoriteArticles()
        }
    }

    suspend fun toggleFavoriteArticle(article: Article) {
        withContext(Dispatchers.IO) {
            val currentArticle = newsDao.getArticleByUrl(article.url)
            currentArticle?.let {
                it.isFavorite = !it.isFavorite
                newsDao.updateArticle(it)
            }
        }
    }

    suspend fun getPersonalizedRecommendations(): List<Article> {
        return withContext(Dispatchers.IO) {
            val userPreferences = userPreferencesRepository.getUserPreferences()
            val response = newsApiService.getTopHeadlines(
                category = userPreferences.preferredCategory,
                query = userPreferences.preferredKeywords
            )
            newsDao.insertArticles(response.articles)
            response.articles
        }
    }
}

// NewsViewModel.kt
class NewsViewModel(
    private val newsRepository: NewsRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    private val _articles = MutableLiveData<List<Article>>()
    val articles: LiveData<List<Article>> = _articles

    private val _favoriteArticles = MutableLiveData<List<Article>>()
    val favoriteArticles: LiveData<List<Article>> = _favoriteArticles

    private val _personalizedRecommendations = MutableLiveData<List<Article>>()
    val personalizedRecommendations: LiveData<List<Article>> = _personalizedRecommendations

    fun getTopHeadlines(
        country: String = "us",
        category: String? = null,
        query: String? = null
    ) {
        viewModelScope.launch {
            val articles = newsRepository.getTopHeadlines(country, category, query)
            _articles.value = articles
        }
    }

    fun searchArticles(query: String, from: String? = null, to: String? = null) {
        viewModelScope.launch {
            val articles = newsRepository.searchArticles(query, from, to)
            _articles.value = articles
        }
    }

    fun getFavoriteArticles() {
        viewModelScope.launch {
            val favoriteArticles = newsRepository.getFavoriteArticles()
            _favoriteArticles.value = favoriteArticles
        }
    }

    fun toggleFavoriteArticle(article: Article) {
        viewModelScope.launch {
            newsRepository.toggleFavoriteArticle(article)
        }
    }

    fun getPersonalizedRecommendations() {
        viewModelScope.launch {
            val recommendations = newsRepository.getPersonalizedRecommendations()
            _personalizedRecommendations.value = recommendations
        }
    }
}

// NewsFragment.kt
class NewsFragment : Fragment() {
    private var _binding: FragmentNewsBinding? = null
    private val binding get() = _binding!!

    private val newsViewModel: NewsViewModel by lazy {
        ViewModelProvider(
            this,
            NewsViewModelFactory(
                NewsRepository(
                    NewsApiService.create(),
                    newsDao,
                    userPreferencesRepository
                ),
                userPreferencesRepository
            )
        ).get(NewsViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val newsAdapter = NewsAdapter(::toggleFavoriteArticle)
        binding.newsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = newsAdapter
        }

        newsViewModel.articles.observe(viewLifecycleOwner) { articles ->
            newsAdapter.submitList(articles)
        }

        newsViewModel.personalizedRecommendations.observe(viewLifecycleOwner) { recommendations ->
            // Display personalized recommendations in a separate view or section
        }

        newsViewModel.getTopHeadlines()
        newsViewModel.getPersonalizedRecommendations()
    }

    private fun toggleFavoriteArticle(article: Article) {
        newsViewModel.toggleFavoriteArticle(article)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
