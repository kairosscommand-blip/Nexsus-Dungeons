package com.nexuscraft.nexusdungeons;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * The pure, in-memory logical shape of one floor's maze -- a grid of {@code cellsX * cellsZ}
 * cells, each linked to its passable neighbors. This is deliberately separate from any real
 * block/world code: {@link MazeGenerator} builds one of these with plain graph algorithms in a
 * single synchronous pass (cheap -- even a few thousand cells is milliseconds of CPU), and
 * {@link DungeonBuilder} is the only thing that ever turns it into real, carved blocks
 * (expensive -- spread across many ticks so it never freezes the server).
 */
final class MazeLayout {

    final int cellsX;
    final int cellsZ;
    final int startX;
    final int startZ;

    /** linkEast[x][z] == true means cell (x,z) has an open passage to cell (x+1,z). Sized
     *  [cellsX][cellsZ]; the last column (x == cellsX - 1) is always false (no cell to its east). */
    private final boolean[][] linkEast;

    /** linkSouth[x][z] == true means cell (x,z) has an open passage to cell (x,z+1). Sized
     *  [cellsX][cellsZ]; the last row (z == cellsZ - 1) is always false (no cell to its south). */
    private final boolean[][] linkSouth;

    MazeLayout(int cellsX, int cellsZ, int startX, int startZ) {
        this.cellsX = cellsX;
        this.cellsZ = cellsZ;
        this.startX = startX;
        this.startZ = startZ;
        this.linkEast = new boolean[cellsX][cellsZ];
        this.linkSouth = new boolean[cellsX][cellsZ];
    }

    boolean inBounds(int x, int z) {
        return x >= 0 && x < cellsX && z >= 0 && z < cellsZ;
    }

    void link(int x1, int z1, int x2, int z2) {
        if (x2 == x1 + 1 && z2 == z1) {
            linkEast[x1][z1] = true;
        } else if (x2 == x1 - 1 && z2 == z1) {
            linkEast[x2][z2] = true;
        } else if (z2 == z1 + 1 && x2 == x1) {
            linkSouth[x1][z1] = true;
        } else if (z2 == z1 - 1 && x2 == x1) {
            linkSouth[x2][z2] = true;
        } else {
            throw new IllegalArgumentException("Cells are not adjacent: (" + x1 + "," + z1 + ") / (" + x2 + "," + z2 + ")");
        }
    }

    boolean isLinkedEast(int x, int z) {
        return x >= 0 && x < cellsX - 1 && z >= 0 && z < cellsZ && linkEast[x][z];
    }

    boolean isLinkedSouth(int x, int z) {
        return x >= 0 && x < cellsX && z >= 0 && z < cellsZ - 1 && linkSouth[x][z];
    }

    boolean isLinkedWest(int x, int z) {
        return isLinkedEast(x - 1, z);
    }

    boolean isLinkedNorth(int x, int z) {
        return isLinkedSouth(x, z - 1);
    }

    int degree(int x, int z) {
        int d = 0;
        if (isLinkedEast(x, z)) d++;
        if (isLinkedWest(x, z)) d++;
        if (isLinkedSouth(x, z)) d++;
        if (isLinkedNorth(x, z)) d++;
        return d;
    }

    /** Breadth-first distance from the start cell to every reachable cell -- a perfect maze (no
     *  loops) reaches every cell exactly once this way; the optional extra loops MazeGenerator can
     *  add don't change that every cell stays reachable, just that more than one path may exist. */
    int[][] distancesFromStart() {
        int[][] dist = new int[cellsX][cellsZ];
        for (int[] row : dist) {
            java.util.Arrays.fill(row, -1);
        }
        dist[startX][startZ] = 0;
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startX, startZ});
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            int cx = cur[0];
            int cz = cur[1];
            int d = dist[cx][cz];
            if (isLinkedEast(cx, cz) && dist[cx + 1][cz] < 0) {
                dist[cx + 1][cz] = d + 1;
                queue.add(new int[]{cx + 1, cz});
            }
            if (isLinkedWest(cx, cz) && dist[cx - 1][cz] < 0) {
                dist[cx - 1][cz] = d + 1;
                queue.add(new int[]{cx - 1, cz});
            }
            if (isLinkedSouth(cx, cz) && dist[cx][cz + 1] < 0) {
                dist[cx][cz + 1] = d + 1;
                queue.add(new int[]{cx, cz + 1});
            }
            if (isLinkedNorth(cx, cz) && dist[cx][cz - 1] < 0) {
                dist[cx][cz - 1] = d + 1;
                queue.add(new int[]{cx, cz - 1});
            }
        }
        return dist;
    }

    /** Every dead end (degree exactly 1) except the start cell -- these are where treasure, crypt,
     *  and prison-cell rooms get placed, same spirit as a real hand-built dungeon tucking its
     *  rewards away from the main thoroughfare. */
    List<int[]> deadEnds() {
        List<int[]> ends = new ArrayList<>();
        for (int x = 0; x < cellsX; x++) {
            for (int z = 0; z < cellsZ; z++) {
                if ((x != startX || z != startZ) && degree(x, z) == 1) {
                    ends.add(new int[]{x, z});
                }
            }
        }
        return ends;
    }

    /** The single cell farthest (by real path distance, not straight-line) from the start --
     *  where the boss room (final floor) or the stairwell down (earlier floors) is placed. */
    int[] farthestCell() {
        int[][] dist = distancesFromStart();
        int bestX = startX;
        int bestZ = startZ;
        int best = -1;
        for (int x = 0; x < cellsX; x++) {
            for (int z = 0; z < cellsZ; z++) {
                if (dist[x][z] > best) {
                    best = dist[x][z];
                    bestX = x;
                    bestZ = z;
                }
            }
        }
        return new int[]{bestX, bestZ};
    }
}
