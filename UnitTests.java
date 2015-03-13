//import MatrixMult.*;
import java.io.*;

public class UnitTests{
    public static void main(String[] args) throws IOException{
            //int[][] file1 = MatrixMult.readFile("i1.txt",j);
            //int[][] file2 = MatrixMult.readFile("j1.txt",j);

            MatrixMult.writeMult("i1.txt", "j1.txt", "k1.txt");
            int[][] fileK = MatrixMult.readFile("k1.txt", 0, 0, 0, 8);
            MatrixMult.printMatrix(fileK);
    }
}
