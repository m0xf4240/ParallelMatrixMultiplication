//import MatrixMult.*;
import java.io.*;

public class UnitTests{
    public static void main(String[] args) throws IOException{
        for (int j=0; j<8; j++){
            int[] file = MatrixMult.readFileCol("j1.txt",j);
            for (int i=0; i<file.length; i++){
                System.out.print(file[i]+",");
            }
            System.out.println();
        }
    }
}
