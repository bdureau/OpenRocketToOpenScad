package orkToScad;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.swing.SwingUtilities;

/**
 * Entry point: opens the ORKtoSCAD window. An optional argument is a .ork file to convert right away
 * ({@code java -jar orkToScad.jar my-rocket.ork}).
 */
public class Main {

    /** Version shown in "About" and in the manual; build-exe.ps1 uses it for the executable. */
    public static final String VERSION = "1.0.0";

    /** Author and contact shown in "About". */
    static final String AUTHOR = "Boris du Reau";
    static final String EMAIL = "boris.dureau@neuf.fr";

    public static void main(String[] args) {
        Path ork = args.length > 0 ? Paths.get(args[0]) : null;
        SwingUtilities.invokeLater(() -> MainWindow.open(ork));
    }
}
