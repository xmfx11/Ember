package com.ember.data.model

// 所有字段可空，禁止 !! 强转，防止解析崩溃
data class EmbyUser(
    val Id: String? = null,
    val Name: String? = null,
    val PrimaryImageTag: String? = null
)

// Emby 登录接口 /Users/AuthenticateByName 要求密码字段名为 Pw（不是 Password）
data class LoginRequest(
    val Username: String,
    val Pw: String
)

data class LoginResponse(
    val User: EmbyUser? = null,
    val AccessToken: String? = null,
    val ServerId: String? = null
)
