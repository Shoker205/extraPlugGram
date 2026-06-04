package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Mapping for TDLib states:
// PHONE: authorizationStateWaitPhoneNumber
// CODE: authorizationStateWaitCode
// PASSWORD: authorizationStateWaitPassword
// AUTHENTICATED: authorizationStateReady
enum class AuthStep {
    PHONE, CODE, PASSWORD, AUTHENTICATED
}

class TelegramAuthViewModel : ViewModel() {
    private val _authState = MutableStateFlow(AuthStep.PHONE)
    val authState = _authState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    var phoneNumber = ""
    var passwordHint = "Password"

    // Mock TDLib auth steps
    fun sendPhoneNumber(phone: String) {
        phoneNumber = phone
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            delay(1500) // Simulate TDLib network delay
            if (phone.isBlank() || phone.length < 5) {
                _error.value = "Invalid phone number formatting"
            } else {
                _authState.value = AuthStep.CODE
            }
            _isLoading.value = false
        }
    }

    fun submitCode(code: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            delay(1500) // Simulate TDLib network delay
            if (code.length != 5) {
                _error.value = "Invalid verification code"
            } else {
                _authState.value = AuthStep.AUTHENTICATED
            }
            _isLoading.value = false
        }
    }

    fun submitPassword(password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            delay(1500)
            if (password == "password") {
                _error.value = "Invalid password"
            } else {
                _authState.value = AuthStep.AUTHENTICATED
            }
            _isLoading.value = false
        }
    }

    fun clearError() { _error.value = null }
    
    fun goBack() {
        when (_authState.value) {
            AuthStep.CODE -> _authState.value = AuthStep.PHONE
            AuthStep.PASSWORD -> _authState.value = AuthStep.CODE
            else -> {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramAuthScreen(
    onAuthSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: TelegramAuthViewModel = viewModel()
) {
    val authStep by viewModel.authState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(authStep) {
        if (authStep == AuthStep.AUTHENTICATED) {
            onAuthSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (authStep == AuthStep.PHONE) onBack()
                        else viewModel.goBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = authStep,
                transitionSpec = {
                    slideInHorizontally { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> -width } + fadeOut()
                },
                label = "auth_animation"
            ) { step ->
                when (step) {
                    AuthStep.PHONE -> PhoneInputView(isLoading, error, viewModel::sendPhoneNumber)
                    AuthStep.CODE -> CodeInputView(viewModel.phoneNumber, isLoading, error, viewModel::submitCode)
                    AuthStep.PASSWORD -> PasswordInputView(viewModel.passwordHint, isLoading, error, viewModel::submitPassword)
                    AuthStep.AUTHENTICATED -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (error != null) {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = {
                        TextButton(onClick = viewModel::clearError) { Text("OK") }
                    }
                ) {
                    Text(error!!)
                }
            }
        }
    }
}

@Composable
fun PhoneInputView(isLoading: Boolean, error: String?, onSubmit: (String) -> Unit) {
    var phone by remember { mutableStateOf("+") }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Send,
            contentDescription = "Telegram",
            modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(32.dp))
        Text("Sign in to Telegram", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Please confirm your country code and enter your phone number.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        
        Spacer(Modifier.height(32.dp))
        
        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!isLoading) onSubmit(phone) }),
            isError = error != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        
        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = { onSubmit(phone) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoading && phone.length > 3,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text("Continue", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun CodeInputView(phone: String, isLoading: Boolean, error: String?, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Chat,
            contentDescription = null,
            modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(32.dp))
        Text(phone, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("We've sent the code to the Telegram app on your other device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        
        Spacer(Modifier.height(32.dp))
        
        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 5) code = it },
            label = { Text("Code") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!isLoading) onSubmit(code) }),
            isError = error != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.6f),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, letterSpacing = 8.sp),
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = { onSubmit(code) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoading && code.length == 5,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text("Verify", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun PasswordInputView(hint: String, isLoading: Boolean, error: String?, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(80.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(32.dp))
        Text("Two-Step Verification", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Your account is protected with an additional password.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        
        Spacer(Modifier.height(32.dp))
        
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(hint) },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!isLoading) onSubmit(password) }),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(image, "Toggle password visibility")
                }
            },
            isError = error != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(Modifier.height(32.dp))
        
        Button(
            onClick = { onSubmit(password) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isLoading && password.isNotEmpty(),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text("Next", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
