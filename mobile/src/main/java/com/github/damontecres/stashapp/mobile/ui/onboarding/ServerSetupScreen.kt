package com.github.damontecres.stashapp.mobile.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.okHttpClient
import com.github.damontecres.stashapp.mobile.R
import com.github.damontecres.stashapp.mobile.api.CredentialsQuery
import com.github.damontecres.stashapp.mobile.api.GenerateApiKeyMutation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

enum class AuthMode {
    API_KEY,
    CREDENTIALS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSetupScreen(
    initialServerUrl: String = "",
    initialApiKey: String = "",
    initialUsername: String = "",
    initialPassword: String = "",
    onServerConfigured: (serverUrl: String, apiKey: String, username: String, password: String) -> Unit
) {
    var serverUrl by remember { mutableStateOf(initialServerUrl) }
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf(initialPassword) }
    var authMode by remember { mutableStateOf(if (initialUsername.isNotBlank()) AuthMode.CREDENTIALS else AuthMode.API_KEY) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.server_setup_title)) }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.server_url_label)) },
                    placeholder = { Text(stringResource(R.string.server_url_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SegmentedButton(
                            selected = authMode == AuthMode.API_KEY,
                            onClick = { authMode = AuthMode.API_KEY },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.auth_mode_api_key),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        SegmentedButton(
                            selected = authMode == AuthMode.CREDENTIALS,
                            onClick = { authMode = AuthMode.CREDENTIALS },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.auth_mode_credentials),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (authMode == AuthMode.API_KEY) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.api_key_label)) },
                        placeholder = { Text(stringResource(R.string.api_key_hint)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.username_label)) },
                        placeholder = { Text(stringResource(R.string.username_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.password_label)) },
                        placeholder = { Text(stringResource(R.string.password_hint)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    enabled = !isTestingConnection,
                    onClick = {
                        val cleanUrl = serverUrl.trim().trimEnd('/')
                        if (cleanUrl.isBlank() || (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://"))) {
                            errorMessage = "Please enter a valid URL starting with http:// or https://"
                            return@Button
                        }

                        isTestingConnection = true
                        errorMessage = null

                        scope.launch {
                            try {
                                val resultApiKey = withContext(Dispatchers.IO) {
                                    verifyAndFetchApiKey(
                                        serverUrl = cleanUrl,
                                        authMode = authMode,
                                        apiKey = apiKey.trim(),
                                        username = username.trim(),
                                        password = password
                                    )
                                }
                                isTestingConnection = false
                                onServerConfigured(cleanUrl, resultApiKey, username.trim(), password)
                            } catch (ex: Exception) {
                                isTestingConnection = false
                                errorMessage = ex.localizedMessage ?: "Failed to connect to Stash server"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying...")
                    } else {
                        Text(stringResource(R.string.connect_button))
                    }
                }
            }
        }
    }
}

private suspend fun verifyAndFetchApiKey(
    serverUrl: String,
    authMode: AuthMode,
    apiKey: String,
    username: String,
    password: String
): String {
    val cleanUrl = serverUrl.trim().trimEnd('/')
    val graphqlEndpoint = "$cleanUrl/graphql"
    val loginUrl = "$cleanUrl/login"

    val cookieJar = object : okhttp3.CookieJar {
        private val cookies = mutableMapOf<String, List<okhttp3.Cookie>>()
        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> = cookies[url.host] ?: listOf()
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            this.cookies[url.host] = cookies
        }
    }

    val okHttpClientBuilder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .cookieJar(cookieJar)

    if (authMode == AuthMode.CREDENTIALS) {
        val clientForLogin = okHttpClientBuilder.build()
        val formBody = FormBody.Builder()
            .add("username", username)
            .add("password", password)
            .build()
        val request = Request.Builder()
            .url(loginUrl)
            .post(formBody)
            .build()

        val loginResponse = withContext(Dispatchers.IO) {
            clientForLogin.newCall(request).execute()
        }

        if (!loginResponse.isSuccessful) {
            throw java.io.IOException("Invalid username or password (HTTP ${loginResponse.code})")
        }
    } else if (apiKey.isNotBlank()) {
        okHttpClientBuilder.addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("ApiKey", apiKey)
                .header("ApiKey-ApiKey", apiKey)
                .build()
            chain.proceed(req)
        }
    }

    val okHttpClient = okHttpClientBuilder.build()
    val apolloClient = ApolloClient.Builder()
        .serverUrl(graphqlEndpoint)
        .okHttpClient(okHttpClient)
        .build()

    val response = apolloClient.query(CredentialsQuery()).execute()

    if (response.hasErrors()) {
        val errorText = response.errors?.firstOrNull()?.message ?: "Server error"
        throw Exception("Server connection error: $errorText")
    }

    var serverApiKey = response.data?.configuration?.general?.apiKey.orEmpty()
    if (serverApiKey.isBlank() && authMode == AuthMode.CREDENTIALS) {
        try {
            val genResponse = apolloClient.mutation(GenerateApiKeyMutation()).execute()
            serverApiKey = genResponse.data?.generateAPIKey.orEmpty()
        } catch (e: Exception) {
            // Ignore if generation not supported
        }
    }

    return if (authMode == AuthMode.API_KEY) apiKey else serverApiKey
}
