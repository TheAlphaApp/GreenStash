/**
 * MIT License
 *
 * Copyright (c) [2022 - Present] Stɑrry Shivɑm
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */


package com.starry.greenstash.ui.screens.input.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.starry.greenstash.database.goal.Goal
import com.starry.greenstash.database.goal.GoalDao
import com.starry.greenstash.database.goal.GoalPriority
import com.starry.greenstash.reminder.ReminderManager
import com.starry.greenstash.utils.ImageUtils
import com.starry.greenstash.utils.Utils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class InputScreenState(
    val goalImageUri: Uri? = null,
    val goalTitleText: String = "",
    val targetAmount: String = "",
    val deadline: String = "",
    val additionalNotes: String = "",
    val priority: String = GoalPriority.Normal.name,
    val reminder: Boolean = false
)

@ExperimentalMaterialApi
@ExperimentalFoundationApi
@ExperimentalComposeUiApi
@ExperimentalAnimationApi
@ExperimentalMaterial3Api
@HiltViewModel
class InputViewModel @Inject constructor(
    private val goalDao: GoalDao,
    private val reminderManager: ReminderManager
) : ViewModel() {

    var state by mutableStateOf(InputScreenState())

    fun addSavingGoal(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val goal = Goal(
                title = state.goalTitleText,
                targetAmount = Utils.roundDecimal(state.targetAmount.toDouble()),
                deadline = state.deadline,
                goalImage = if (state.goalImageUri != null) ImageUtils.uriToBitmap(
                    uri = state.goalImageUri!!, context = context, maxSize = 1024
                ) else null,
                additionalNotes = state.additionalNotes,
                priority = GoalPriority.values().find { it.name == state.priority }!!,
                reminder = state.reminder
            )

            // Add goal into database.
            val goalId = goalDao.insertGoal(goal)
            // schedule reminder if it's enabled.
            if (goal.reminder) {
                reminderManager.scheduleReminder(goalId)
            }
        }
    }

    fun setEditGoalData(goalId: Long, onEditDataSet: (Bitmap?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val goal = goalDao.getGoalById(goalId)!!
            withContext(Dispatchers.Main) {
                state = state.copy(
                    goalTitleText = goal.title,
                    targetAmount = goal.targetAmount.toString(),
                    deadline = goal.deadline,
                    additionalNotes = goal.additionalNotes,
                    priority = goal.priority.name,
                    reminder = goal.reminder
                )
                onEditDataSet(goal.goalImage)
            }
        }
    }

    fun editSavingGoal(goalId: Long, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val goal = goalDao.getGoalById(goalId)!!
            val newGoal = Goal(
                title = state.goalTitleText,
                targetAmount = Utils.roundDecimal(state.targetAmount.toDouble()),
                deadline = state.deadline,
                goalImage = if (state.goalImageUri != null) ImageUtils.uriToBitmap(
                    uri = state.goalImageUri!!, context = context, maxSize = 1024
                ) else goal.goalImage,
                additionalNotes = state.additionalNotes,
                priority = GoalPriority.values().find { it.name == state.priority }!!,
                reminder = state.reminder
            )
            // copy id of already saved goal to update it.
            newGoal.goalId = goal.goalId
            goalDao.updateGoal(newGoal)

            // Handle possible changes made in reminders.
            if (newGoal.reminder) {
                if (!reminderManager.isReminderSet(goalId))
                    reminderManager.scheduleReminder(goalId)
            } else {
                reminderManager.stopReminder(goalId)
            }
        }
    }

    fun removeDeadLine() {
        state = state.copy(deadline = "")
    }

}