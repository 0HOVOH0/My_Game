package ncu.cs2.my_game.effect;

import javafx.scene.image.Image;

import java.util.List;

public class FrameAnimation {
    private final List<Image> frames;
    private final double duration;

    public FrameAnimation(List<Image> frames, double duration) {
        this.frames = List.copyOf(frames);
        this.duration = duration;
    }

    public Image frameAt(double elapsed) {
        if (frames.isEmpty()) return null;
        int index = Math.min(frames.size() - 1,
            (int) Math.floor((elapsed / Math.max(0.001, duration)) * frames.size()));
        return frames.get(index);
    }

    public double duration() {
        return duration;
    }

    public boolean isEmpty() {
        return frames.isEmpty();
    }

    public int frameCount() {
        return frames.size();
    }
}
