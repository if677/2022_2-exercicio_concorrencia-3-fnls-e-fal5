package support;

import javax.swing.*;
import java.util.Objects;

public class Icons {
    public static ImageIcon[] getIcons24(){
        ImageIcon next = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/next-24.png")));
        ImageIcon pause = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/pause-24.png")));
        ImageIcon play = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/play-24.png")));
        ImageIcon previous = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/previous-24.png")));
        ImageIcon loop = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/loop-24.png")));
        ImageIcon shuffle = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/shuffle-24.png")));
        ImageIcon stop = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/stop-24.png")));
        return new ImageIcon[]{next, pause, play, previous, loop, shuffle, stop};
    }

    public static ImageIcon[] getIcons48(){
        ImageIcon next = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/next-48.png")));
        ImageIcon pause = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/pause-48.png")));
        ImageIcon play = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/play-48.png")));
        ImageIcon previous = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/previous-48.png")));
        ImageIcon loop = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/loop-48.png")));
        ImageIcon shuffle = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/shuffle-48.png")));
        ImageIcon stop = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/stop-48.png")));
        return new ImageIcon[]{next, pause, play, previous, loop, shuffle, stop};
    }

    public static ImageIcon[] getIcons96(){
        ImageIcon next = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/next-96.png")));
        ImageIcon pause = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/pause-96.png")));
        ImageIcon play = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/play-96.png")));
        ImageIcon previous = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/previous-96.png")));
        ImageIcon loop = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/loop-96.png")));
        ImageIcon shuffle = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/shuffle-96.png")));
        ImageIcon stop = new ImageIcon(Objects.requireNonNull(Icons.class.getResource("/icons/stop-96.png")));
        return new ImageIcon[]{next, pause, play, previous, loop, shuffle, stop};
    }
}
