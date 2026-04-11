package com.somdubstep.som;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class SelfOrganizingMap {
    public final int width, height, inputDim;
    public float learningRate = 0.3f;

    /** Grid radius used for neighbor influence (decays during training). */
    private final double radius0;

    /** Public neuron list so UI code can render easily. */
    public final List<Neuron> neurons = new ArrayList<>();

    /** Optional: for incremental/online training decay */
    private long stepsTrained = 0;

    public static final class Neuron {
        public final int x, y;
        public final float[] weights;
        Neuron(int x, int y, int dim, Random rnd) {
            this.x = x; this.y = y;
            this.weights = new float[dim];
            for (int i = 0; i < dim; i++) this.weights[i] = rnd.nextFloat();
        }
        
        public float[] getWeights() { return weights; }
    }

    /** Seeded ctor (useful for reproducible tests) */
    public SelfOrganizingMap(int width, int height, int inputDim, long seed) {
        this.width = width; this.height = height; this.inputDim = inputDim;
        this.radius0 = Math.max(width, height) / 2.0;
        Random rnd = new Random(seed);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                neurons.add(new Neuron(x, y, inputDim, rnd));
            }
        }
    }

    /** Convenience ctor with random seed. */
    public SelfOrganizingMap(int width, int height, int inputDim) {
        this(width, height, inputDim, System.nanoTime());
    }

    public int size() { return neurons.size(); }

    /** Find index of Best Matching Unit for input vector. */
    public int findBMU(float[] in) {
        int best = 0;
        double bestD = Double.POSITIVE_INFINITY;
        for (int i = 0; i < neurons.size(); i++) {
            double d = 0;
            float[] w = neurons.get(i).weights;
            for (int k = 0; k < inputDim; k++) {
                double dk = (in[k] - w[k]);
                d += dk * dk;
            }
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    /** Batch-style training for a number of epochs over the dataset. */
    public void train(float[][] inputs, int epochs) {
        if (inputs == null || inputs.length == 0) return;
        long totalSteps = (long) epochs * inputs.length;
        for (int e = 0; e < epochs; e++) {
            double frac = (double) e / Math.max(1, epochs);
            // simple linear (or exp) decay across epochs
            double radius = radius0 * (1.0 - frac);
            double lr = learningRate * (1.0 - frac);
            if (radius < 1e-6) radius = 1e-6;
            if (lr < 1e-6) lr = 1e-6;

            for (float[] in : inputs) {
                stepsTrained++;
                int bmuIdx = findBMU(in);
                Neuron bmu = neurons.get(bmuIdx);

                for (int i = 0; i < neurons.size(); i++) {
                    Neuron n = neurons.get(i);
                    double dx = n.x - bmu.x;
                    double dy = n.y - bmu.y;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist <= radius) {
                        double influence = Math.exp(-(dist * dist) / (2.0 * radius * radius + 1e-9));
                        for (int k = 0; k < inputDim; k++) {
                            n.weights[k] += (float) (lr * influence * (in[k] - n.weights[k]));
                        }
                    }
                }
            }
        }
    }

    /** Online/incremental training (good for “keep learning as new images arrive”). */
    public void trainOnline(float[][] inputs, int epochs) {
        if (inputs == null || inputs.length == 0) return;
        long total = Math.max(1L, (long) epochs * inputs.length);
        for (int e = 0; e < epochs; e++) {
            for (float[] in : inputs) {
                long t = stepsTrained++;
                // exponential global decay over lifetime
                double decay = Math.exp(-(double) t / total);
                double radius = Math.max(1e-4, radius0 * decay);
                double lr = Math.max(1e-5, learningRate * decay);

                int bmuIdx = findBMU(in);
                Neuron bmu = neurons.get(bmuIdx);

                for (int i = 0; i < neurons.size(); i++) {
                    Neuron n = neurons.get(i);
                    double dx = n.x - bmu.x;
                    double dy = n.y - bmu.y;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist <= radius) {
                        double influence = Math.exp(-(dist * dist) / (2.0 * radius * radius + 1e-9));
                        for (int k = 0; k < inputDim; k++) {
                            n.weights[k] += (float) (lr * influence * (in[k] - n.weights[k]));
                        }
                    }
                }
            }
        }
    }

	public Neuron getNuronWith(int x, int y) {

		for (Iterator iterator = neurons.iterator(); iterator.hasNext();) {
			Neuron neuron = (Neuron) iterator.next();
			if (neuron.x == x && neuron.y == y) {
				return neuron;
			}
		}
		
		return null;
	}
}
