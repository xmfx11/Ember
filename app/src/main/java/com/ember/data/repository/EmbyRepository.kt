package com.ember.data.repository

import com.ember.data.api.EmbyService
import com.ember.data.model.EmbyItem
import com.ember.data.model.EmbyUser
import com.ember.data.model.LoginRequest
import com.ember.data.model.LoginResponse
import com.ember.utils.AppLogger
import com.ember.utils.ErrorTranslator

/**
 * 单一数据源，所有请求走这里
 * 所有方法返回 Result，禁止抛异常给 UI
 * 所有请求记日志，便于排查
 */
class EmbyRepository(private val apiService: EmbyService) {

    // 登录
    suspend fun login(username: String, password: String): Result<LoginResponse> = try {
        AppLogger.i("API login user=$username")
        val response = apiService.login(LoginRequest(Username = username, Pw = password))
        if (response.isSuccessful && response.body() != null) {
            AppLogger.i("API login ok, tokenLen=${response.body()!!.AccessToken?.length}")
            Result.success(response.body()!!)
        } else {
            val err = response.errorBody()?.string() ?: ""
            AppLogger.w("API login failed ${response.code()}: $err")
            Result.failure(Exception("登录失败 ${response.code()}: $err"))
        }
    } catch (e: Exception) {
        AppLogger.e("API login exception", e)
        Result.failure(Exception(ErrorTranslator.translate(e)))
    }

    // 验证 token（自动登录用）
    suspend fun getUser(userId: String): Result<EmbyUser> = try {
        AppLogger.d("API getUser userId=$userId")
        val response = apiService.getUser(userId)
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!)
        } else {
            AppLogger.w("API getUser failed ${response.code()}")
            Result.failure(Exception("用户验证失败 ${response.code()}"))
        }
    } catch (e: Exception) {
        AppLogger.e("API getUser exception", e)
        Result.failure(Exception(ErrorTranslator.translate(e)))
    }

    // 媒体库列表（首页入口）
    suspend fun getViews(userId: String): Result<List<EmbyItem>> = try {
        AppLogger.d("API getViews userId=$userId")
        val response = apiService.getViews(userId)
        if (response.isSuccessful && response.body() != null) {
            val items = response.body()!!.Items ?: emptyList()
            AppLogger.i("API getViews ok, count=${items.size}")
            Result.success(items)
        } else {
            AppLogger.w("API getViews failed ${response.code()}")
            Result.failure(Exception("获取媒体库失败 ${response.code()}"))
        }
    } catch (e: Exception) {
        AppLogger.e("API getViews exception", e)
        Result.failure(Exception(ErrorTranslator.translate(e)))
    }

    // 媒体库内容
    suspend fun getItems(userId: String, parentId: String? = null): Result<List<EmbyItem>> = try {
        AppLogger.d("API getItems userId=$userId parentId=$parentId")
        val response = apiService.getItems(userId, parentId)
        if (response.isSuccessful && response.body() != null) {
            val items = response.body()!!.Items ?: emptyList()
            AppLogger.i("API getItems ok, count=${items.size}")
            Result.success(items)
        } else {
            AppLogger.w("API getItems failed ${response.code()}")
            Result.failure(Exception("获取内容失败 ${response.code()}"))
        }
    } catch (e: Exception) {
        AppLogger.e("API getItems exception", e)
        Result.failure(Exception(ErrorTranslator.translate(e)))
    }

    // 获取单个项目详情（详情页用，走独立接口）
    suspend fun getItem(userId: String, itemId: String): Result<EmbyItem> = try {
        AppLogger.d("API getItem userId=$userId itemId=$itemId")
        val response = apiService.getItem(userId, itemId)
        if (response.isSuccessful && response.body() != null) {
            AppLogger.i("API getItem ok name=${response.body()!!.Name}")
            Result.success(response.body()!!)
        } else {
            AppLogger.w("API getItem failed ${response.code()}")
            Result.failure(Exception("获取详情失败 ${response.code()}"))
        }
    } catch (e: Exception) {
        AppLogger.e("API getItem exception", e)
        Result.failure(Exception(ErrorTranslator.translate(e)))
    }

    // 按标签筛选
    suspend fun getItemsByTag(userId: String, tag: String): Result<List<EmbyItem>> = try {
        AppLogger.d("API getItemsByTag tag=$tag")
        val response = apiService.getItemsByTag(userId, tag)
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!.Items ?: emptyList())
        } else {
            Result.failure(Exception("标签筛选失败 ${response.code()}"))
        }
    } catch (e: Exception) {
        AppLogger.e("API getItemsByTag exception", e)
        Result.failure(Exception(ErrorTranslator.translate(e)))
    }

    // 按演员筛选
    suspend fun getItemsByPerson(userId: String, personId: String): Result<List<EmbyItem>> = try {
        AppLogger.d("API getItemsByPerson personId=$personId")
        val response = apiService.getItemsByPerson(userId, personId)
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!.Items ?: emptyList())
        } else {
            Result.failure(Exception("演员筛选失败 ${response.code()}"))
        }
    } catch (e: Exception) {
        AppLogger.e("API getItemsByPerson exception", e)
        Result.failure(Exception(ErrorTranslator.translate(e)))
    }

    // 搜索
    suspend fun search(userId: String, term: String): Result<List<EmbyItem>> = try {
        AppLogger.d("API search term=$term")
        val response = apiService.search(userId, term)
        if (response.isSuccessful && response.body() != null) {
            Result.success(response.body()!!.SearchHints ?: emptyList())
        } else {
            Result.failure(Exception("搜索失败 ${response.code()}"))
        }
    } catch (e: Exception) {
        AppLogger.e("API search exception", e)
        Result.failure(Exception(ErrorTranslator.translate(e)))
    }

    // 播放信息（提取直链）
    suspend fun getPlaybackInfo(itemId: String, userId: String): Result<List<com.ember.data.model.MediaSource>> = try {
        AppLogger.d("API getPlaybackInfo itemId=$itemId")
        val response = apiService.getPlaybackInfo(itemId, userId)
        if (response.isSuccessful && response.body() != null) {
            val sources = response.body()!!.MediaSources ?: emptyList()
            AppLogger.i("API getPlaybackInfo ok, sources=${sources.size}")
            Result.success(sources)
        } else {
            AppLogger.w("API getPlaybackInfo failed ${response.code()}")
            Result.failure(Exception("获取播放信息失败 ${response.code()}"))
        }
    } catch (e: Exception) {
        AppLogger.e("API getPlaybackInfo exception", e)
        Result.failure(Exception(ErrorTranslator.translate(e)))
    }
}
