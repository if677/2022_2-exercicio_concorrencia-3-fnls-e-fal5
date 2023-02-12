package support;

import java.awt.*;
import java.io.IOException;
import java.util.Objects;

public class Fonts {
    public static Font getRoboto() {
        Font roboto;
        try {
            roboto = Font.createFont(
                    Font.TRUETYPE_FONT,
                    (Objects.requireNonNull(Fonts.class.getResourceAsStream("/fonts/roboto_condensed/RobotoCondensed-Regular.ttf"))));
        } catch (FontFormatException | IOException e) {
            throw new RuntimeException(e);
        }
        return roboto;
    }
}
