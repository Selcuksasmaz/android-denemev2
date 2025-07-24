package com.example.zedeneme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.zedeneme.ui.screen.FaceRecognitionScreen
import com.example.zedeneme.ui.screen.FaceRegistrationScreen
import com.example.zedeneme.ui.screen.HomeScreen
import com.example.zedeneme.ui.theme.ZedenemeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZedenemeTheme {
                FaceRecognitionApp()
            }
        }
    }
}

@Composable
fun FaceRecognitionApp() {
    val navController = rememberNavController()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        FaceRecognitionNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
fun FaceRecognitionNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(
                onNavigateToRegistration = {
                    navController.navigate("registration")
                },
                onNavigateToRecognition = {
                    navController.navigate("recognition")
                }
            )
        }

        composable("registration") {
            FaceRegistrationScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("recognition") {
            FaceRecognitionScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}