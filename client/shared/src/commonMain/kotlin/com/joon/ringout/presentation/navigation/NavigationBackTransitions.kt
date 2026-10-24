package com.joon.ringout.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.defaultPopTransitionSpec
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec

internal expect val supportsPredictiveBackTransitions: Boolean

internal class NavigationBackTransitions(
    val enabled: Boolean,
    val pop: AnimatedContentTransitionScope<Scene<AppRoute>>.() -> ContentTransform,
    val predictivePop: AnimatedContentTransitionScope<Scene<AppRoute>>.(Int) -> ContentTransform,
)

internal fun platformNavigationBackTransitions(): NavigationBackTransitions = NavigationBackTransitions(
    enabled = supportsPredictiveBackTransitions,
    pop = defaultPopTransitionSpec(),
    predictivePop = defaultPredictivePopTransitionSpec(),
)

internal fun noNavigationTransition(): ContentTransform = EnterTransition.None togetherWith ExitTransition.None
