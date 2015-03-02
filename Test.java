import java.util.*;

public class Test{
    public static void main(String[] args){
        Scanner keyboard = new Scanner(System.in);
        System.out.println("enter an integer");
	    int myint = keyboard.nextInt();
	    while (myint!=0) {
            double log = log(8,myint);
            int offset = myint%8;

            int from = myint-offset;
           
            int closestFake=0;

            System.out.println("Correct me ("+(int)log+")");
            for (int i=(int)log; i>1; i--){
                int term = (int)Math.pow(8,i);
                int backup = closestFake;
                while (closestFake < myint){
                    closestFake += term;
                    System.out.print(closestFake+",");
                    if (closestFake == myint){
                        closestFake = (i==(int)log)?term:backup;
                        System.out.print(closestFake+"=,");
                        i=0;
                        break;
                    }
                    if (closestFake > myint){
                        closestFake -= term;
                        System.out.print(closestFake+">,");
                        break;
                    }
                }
                System.out.print("*");
                if (closestFake == myint){
                    closestFake -= term;
                    break;
                }
                System.out.print(closestFake+",|");
            }
            System.out.println();
 
            if ( myint > closestFake && offset==0){
                System.out.println("From is "+from);
                System.out.println("Correct me ("+(int)log+")");
                from = closestFake;
            }
            if ( myint <=64 && myint%8==0){
                from =0;
            }
            if ( log == (int)log ){
                from = 0;
            }


            System.out.println("Recv from: "+from);
	        myint = keyboard.nextInt();
        }
    }
    public static double log(int base, int num) {
        return Math.log(num) / Math.log(base);
    }
}
