package com.guardia.app.ui.screens.decoy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * Decoy environment shown for the decoy/panic PIN: a fully playable Snake game so an
 * intruder believes they opened an ordinary game app while the real Guardia stays hidden.
 */

private const val GRID = 17

private data class Cell(val x: Int, val y: Int)
private enum class Dir(val dx: Int, val dy: Int) { UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0) }

private val Board = Color(0xFF11321E)
private val BoardAlt = Color(0xFF0E2A19)
private val SnakeHead = Color(0xFF8BE45B)
private val SnakeBody = Color(0xFF5BC24A)
private val Apple = Color(0xFFE5484D)

@Composable
fun DecoyScreen() {
    var snake by remember { mutableStateOf(listOf(Cell(8, 8), Cell(7, 8), Cell(6, 8))) }
    var dir by remember { mutableStateOf(Dir.RIGHT) }
    var pendingDir by remember { mutableStateOf(Dir.RIGHT) }
    var food by remember { mutableStateOf(Cell(12, 8)) }
    var score by remember { mutableIntStateOf(0) }
    var best by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var gameOver by remember { mutableStateOf(false) }

    fun reset() {
        snake = listOf(Cell(8, 8), Cell(7, 8), Cell(6, 8))
        dir = Dir.RIGHT
        pendingDir = Dir.RIGHT
        food = randomFood(snake)
        score = 0
        gameOver = false
        running = true
    }

    LaunchedEffect(running, gameOver) {
        if (!running || gameOver) return@LaunchedEffect
        while (true) {
            delay((150L - score * 3L).coerceAtLeast(70L))
            dir = if (!pendingDir.isOpposite(dir)) pendingDir else dir
            val head = snake.first()
            val next = Cell(head.x + dir.dx, head.y + dir.dy)
            if (next.x < 0 || next.y < 0 || next.x >= GRID || next.y >= GRID || snake.contains(next)) {
                gameOver = true
                running = false
                if (score > best) best = score
                break
            }
            snake = if (next == food) {
                score++
                val grown = listOf(next) + snake
                food = randomFood(grown)
                grown
            } else {
                listOf(next) + snake.dropLast(1)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1F12))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Snake", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ScorePill("SCORE", score)
            ScorePill("BEST", best)
        }
        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Board)
                .pointerInput(Unit) {
                    var dx = 0f
                    var dy = 0f
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f },
                        onDragEnd = {
                            if (abs(dx) > abs(dy)) {
                                pendingDir = if (dx > 0) Dir.RIGHT else Dir.LEFT
                            } else {
                                pendingDir = if (dy > 0) Dir.DOWN else Dir.UP
                            }
                        },
                    ) { change, amount ->
                        change.consume()
                        dx += amount.x
                        dy += amount.y
                    }
                }
                .pointerInput(gameOver, running) {
                    detectTapGestures(onTap = { if (!running || gameOver) reset() })
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cell = size.width / GRID
                // Subtle checkerboard for a polished game-board look.
                for (gx in 0 until GRID) {
                    for (gy in 0 until GRID) {
                        if ((gx + gy) % 2 == 0) {
                            drawRect(
                                color = BoardAlt,
                                topLeft = Offset(gx * cell, gy * cell),
                                size = Size(cell, cell),
                            )
                        }
                    }
                }
                // Apple.
                drawCircle(
                    color = Apple,
                    radius = cell * 0.4f,
                    center = Offset(food.x * cell + cell / 2, food.y * cell + cell / 2),
                )
                // Snake.
                snake.forEachIndexed { i, c ->
                    drawRoundRect(
                        color = if (i == 0) SnakeHead else SnakeBody,
                        topLeft = Offset(c.x * cell + cell * 0.06f, c.y * cell + cell * 0.06f),
                        size = Size(cell * 0.88f, cell * 0.88f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.3f),
                    )
                }
            }

            if (!running) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC0A1F12)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (gameOver) "Game Over" else "Snake",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (gameOver) {
                        Spacer(Modifier.height(8.dp))
                        Text("Score $score", color = Color(0xFFB9D8C2), fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (gameOver) "Tap to play again" else "Tap to start \u2022 swipe to steer",
                        color = Color(0xFF8BE45B),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Swipe on the board to control the snake.",
            color = Color(0xFF6E8C79),
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ScorePill(label: String, value: Int) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF11321E))
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = Color(0xFF6E8C79), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("$value", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

private fun Dir.isOpposite(other: Dir): Boolean =
    (this == Dir.UP && other == Dir.DOWN) || (this == Dir.DOWN && other == Dir.UP) ||
        (this == Dir.LEFT && other == Dir.RIGHT) || (this == Dir.RIGHT && other == Dir.LEFT)

private fun randomFood(snake: List<Cell>): Cell {
    val free = ArrayList<Cell>(GRID * GRID)
    for (x in 0 until GRID) for (y in 0 until GRID) {
        val c = Cell(x, y)
        if (!snake.contains(c)) free.add(c)
    }
    return if (free.isEmpty()) Cell(0, 0) else free.random()
}
