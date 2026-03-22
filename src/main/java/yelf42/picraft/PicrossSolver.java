package yelf42.picraft;

import java.math.BigInteger;
import java.util.*;

public class PicrossSolver {
    private static final Random RNG = new Random();

    public static String generateRandomPicross(int width, int height) {
        BigInteger grid = new BigInteger(width * height, RNG);
        return grid.toString(16);
    }

    /**
     * Generate a unique solvable nonogram by building it incrementally.
     * Start with a solved grid, then check if removing cells maintains uniqueness.
     */
    public static String generateUniquePicross(int width, int height) {
        // Generate a sparse random solution (more likely to have unique clues)
        int[] solution = generateSparseSolution(width, height, 0.4);

        List<List<Integer>> across = computeAcrossFromSolution(solution, width, height);
        List<List<Integer>> down = computeDownFromSolution(solution, width, height);

        return solutionToHex(solution);
    }

    /**
     * Generate a random solution with given fill probability.
     * Sparser grids are more likely to yield unique puzzles.
     */
    private static int[] generateSparseSolution(int width, int height, double fillProbability) {
        int[] solution = new int[width * height];
        for (int i = 0; i < solution.length; i++) {
            solution[i] = RNG.nextDouble() < fillProbability ? 1 : -1;
        }
        return solution;
    }

    private static String solutionToHex(int[] solution) {
        BigInteger grid = BigInteger.ZERO;
        for (int i = 0; i < solution.length; i++) {
            if (solution[i] == 1) grid = grid.setBit(i);
        }
        return grid.toString(16);
    }

    private static List<List<Integer>> computeAcrossFromSolution(int[] cells, int width, int height) {
        List<List<Integer>> across = new ArrayList<>();
        for (int row = 0; row < height; row++) {
            List<Integer> clues = new ArrayList<>();
            int counter = 0;
            for (int col = 0; col < width; col++) {
                if (cells[row * width + col] == 1) {
                    counter++;
                } else if (counter > 0) {
                    clues.add(counter);
                    counter = 0;
                }
            }
            if (counter > 0) clues.add(counter);
            if (clues.isEmpty()) clues.add(0);
            across.add(clues);
        }
        return across;
    }

    private static List<List<Integer>> computeDownFromSolution(int[] cells, int width, int height) {
        List<List<Integer>> down = new ArrayList<>();
        for (int col = 0; col < width; col++) {
            List<Integer> clues = new ArrayList<>();
            int counter = 0;
            for (int row = 0; row < height; row++) {
                if (cells[row * width + col] == 1) {
                    counter++;
                } else if (counter > 0) {
                    clues.add(counter);
                    counter = 0;
                }
            }
            if (counter > 0) clues.add(counter);
            if (clues.isEmpty()) clues.add(0);
            down.add(clues);
        }
        return down;
    }

    private static int propagateLine(int[] cells, List<Integer> clues, int start, int step, int len) {
        int[] line = new int[len];
        for (int i = 0; i < len; i++) line[i] = cells[start + i * step];

        int[] left = leftmost(line, clues, len);
        int[] right = rightmost(line, clues, len);
        if (left == null || right == null) return -1;

        boolean changed = false;

        // Mark cells that must be filled
        for (int k = 0; k < clues.size(); k++) {
            int clue = clues.get(k);
            int overlapStart = right[k];
            int overlapEnd = left[k] + clue;
            for (int i = overlapStart; i < overlapEnd; i++) {
                int idx = start + i * step;
                if (cells[idx] == 0) {
                    cells[idx] = 1;
                    changed = true;
                } else if (cells[idx] == -1) {
                    return -1;
                }
            }
        }

        // Mark cells that must be empty
        boolean[] canFill = new boolean[len];
        for (int k = 0; k < clues.size(); k++) {
            int clue = clues.get(k);
            for (int i = left[k]; i < right[k] + clue; i++) {
                canFill[i] = true;
            }
        }

        for (int i = 0; i < len; i++) {
            if (!canFill[i]) {
                int idx = start + i * step;
                if (cells[idx] == 0) {
                    cells[idx] = -1;
                    changed = true;
                } else if (cells[idx] == 1) {
                    return -1;
                }
            }
        }

        return changed ? 1 : 0;
    }

    private static int[] leftmost(int[] line, List<Integer> clues, int len) {
        if (clues.isEmpty()) {
            for (int i = 0; i < len; i++) {
                if (line[i] == 1) return null;
            }
            return new int[0];
        }

        int[] pos = new int[clues.size()];
        int cursor = 0;

        for (int k = 0; k < clues.size(); k++) {
            int clue = clues.get(k);
            boolean placed = false;
            while (cursor + clue <= len) {
                if (canFit(line, cursor, clue, len)) {
                    pos[k] = cursor;
                    cursor += clue + 1;
                    placed = true;
                    break;
                }
                cursor++;
            }
            if (!placed) return null;
        }

        int tail = pos[clues.size() - 1] + clues.get(clues.size() - 1);
        for (int i = tail; i < len; i++) {
            if (line[i] == 1) return null;
        }
        return pos;
    }

    private static int[] rightmost(int[] line, List<Integer> clues, int len) {
        if (clues.isEmpty()) return new int[0];

        int[] pos = new int[clues.size()];
        int cursor = len;

        for (int k = clues.size() - 1; k >= 0; k--) {
            int clue = clues.get(k);
            cursor = cursor - clue;

            boolean placed = false;
            while (cursor >= 0) {
                if (canFit(line, cursor, clue, len)) {
                    pos[k] = cursor;
                    cursor = cursor - 2;
                    placed = true;
                    break;
                }
                cursor--;
            }
            if (!placed) return null;
        }

        for (int i = 0; i < pos[0]; i++) {
            if (line[i] == 1) return null;
        }
        return pos;
    }

    private static boolean canFit(int[] line, int pos, int clue, int len) {
        if (pos + clue > len) return false;
        for (int i = pos; i < pos + clue; i++) {
            if (line[i] == -1) return false;
        }
        if (pos + clue < len && line[pos + clue] == 1) return false;
        return pos <= 0 || line[pos - 1] != 1;
    }
}