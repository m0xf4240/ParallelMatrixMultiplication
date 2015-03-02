import java.util.*;
public class Test{
    public static void main(String[] args){
        Scanner keyboard = new Scanner(System.in);
        System.out.println("enter an integer");
        int myint = keyboard.nextInt();

        double log = log(8,myint);
        System.out.println("Log is "+log);
        double fdlog = Math.floor(log);
        System.out.println("Floored log is "+fdlog);

        for (double i=fdlog; i>0; i--){    
	        double pow = Math.pow(8,i);
	        System.out.println("Pow is "+pow);
	        double newInt = myint-pow;
	        System.out.println("New int is "+newInt);
	        double offset = newInt%pow;
	        System.out.println("Inner offset is "+offset);
	        double from = myint -offset;
	        if (offset ==0){
	            System.out.println("Recv from "+pow);
	        } else {
	            System.out.println("Recv from "+from);
	        }
        }

    }
    public static double log(int base, int num) {
        return Math.log(num) / Math.log(base);
    }

}
