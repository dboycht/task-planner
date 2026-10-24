package com.dboycht.taskplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dboycht.taskplanner.ui.TaskPlannerRoot
import com.dboycht.taskplanner.ui.theme.TaskPlannerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TaskPlannerTheme {
                TaskPlannerRoot()
            }
        }
    }
}
