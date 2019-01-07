import view.MainWindow;

import javax.swing.*;
import java.io.FileNotFoundException;
import java.io.PrintStream;

public class Main {

    public static void main(String[] args) {

        try {
            PrintStream fileOut = new PrintStream("./log.txt");
            System.setOut(fileOut);
            System.setErr(fileOut);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        MainWindow mainWindow = new MainWindow();
        mainWindow.setVisible(true);
        mainWindow.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    }
}
