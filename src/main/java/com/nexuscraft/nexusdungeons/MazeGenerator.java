package com.nexuscraft.nexusdungeons;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Builds a {@link MazeLayout} with a randomized depth-first-search "recursive backtracker" --
 * the standard algorithm for a real, solvable, single-path-everywhere maze full of genuine dead
 * ends. Implemented iteratively with an explicit stack (never real Java recursion) specifically
 * so this stays correct at real "biggest we can make it" scale -- a naive recursive version would
 * blow the call stack somewhere in the low thousands of cells, and a huge dungeon is exactly the
 * point of this plugin.
 */
final class MazeGenerator {

    private MazeGenerator() {
    }

    static MazeLayout generate(int cellsX, int cellsZ, int startX, int startZ, long seed, double extraLoopChance) {
        MazeLayout maze = new MazeLayout(cellsX, cellsZ, startX, startZ);
        Random random = new Random(seed);
        boolean[][] visited = new boolean[cellsX][cellsZ];
        Deque<int[]> stack = new ArrayDeque<>();

        visited[startX][startZ] = true;
        stack.push(new int[]{startX, startZ});

        while (!stack.isEmpty()) {
            int[] current = stack.peek();
            List<int[]> unvisitedNeighbors = unvisitedNeighborsOf(maze, visited, current[0], current[1]);
            if (unvisitedNeighbors.isEmpty()) {
                stack.pop();
                continue;
            }
            int[] next = unvisitedNeighbors.get(random.nextInt(unvisitedNeighbors.size()));
            maze.link(current[0], current[1], next[0], next[1]);
            visited[next[0]][next[1]] = true;
            stack.push(next);
        }

        if (extraLoopChance > 0) {
            addExtraLoops(maze, random, extraLoopChance);
        }
        return maze;
    }

    private static List<int[]> unvisitedNeighborsOf(MazeLayout maze, boolean[][] visited, int x, int z) {
        List<int[]> neighbors = new ArrayList<>(4);
        addIfUnvisited(maze, visited, neighbors, x + 1, z);
        addIfUnvisited(maze, visited, neighbors, x - 1, z);
        addIfUnvisited(maze, visited, neighbors, x, z + 1);
        addIfUnvisited(maze, visited, neighbors, x, z - 1);
        return neighbors;
    }

    private static void addIfUnvisited(MazeLayout maze, boolean[][] visited, List<int[]> out, int x, int z) {
        if (maze.inBounds(x, z) && !visited[x][z]) {
            out.add(new int[]{x, z});
        }
    }

    /** Knocks down a few extra walls between already-linked-elsewhere neighbors -- turns a subset
     *  of what would otherwise be a single perfect path into a real branching maze with more than
     *  one way through (the "tricks" -- a dead-seeming loop that actually reconnects, a shortcut
     *  you have to already know about) without touching so many walls that it stops being a maze
     *  at all. Both cells are guaranteed already-visited/linked into the maze by the time this
     *  runs, so every added loop is a genuine alternate route, never a random isolated tunnel. */
    private static void addExtraLoops(MazeLayout maze, Random random, double chance) {
        for (int x = 0; x < maze.cellsX; x++) {
            for (int z = 0; z < maze.cellsZ; z++) {
                if (x < maze.cellsX - 1 && !maze.isLinkedEast(x, z) && random.nextDouble() < chance) {
                    maze.link(x, z, x + 1, z);
                }
                if (z < maze.cellsZ - 1 && !maze.isLinkedSouth(x, z) && random.nextDouble() < chance) {
                    maze.link(x, z, x, z + 1);
                }
            }
        }
    }

    /** A shuffled copy, for callers that need to walk dead ends (or any cell list) in random
     *  order without disturbing the original list's meaning (e.g. MazeLayout#deadEnds() itself). */
    static <T> List<T> shuffledCopy(List<T> list, Random random) {
        List<T> copy = new ArrayList<>(list);
        Collections.shuffle(copy, random);
        return copy;
    }
}
