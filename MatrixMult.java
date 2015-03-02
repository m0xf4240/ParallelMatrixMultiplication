import mpi.*;
import java.io.*;

public class MatrixMult{
    public static void main(String[] args) throws MPIException,IOException,InterruptedException{
        MPI.Init(args);


        boolean sleep=true;
        int my_rank; // Rank of process int source;  // Rank of sender
        int dest;    // Rank of receiver 
        int tag=50;  // Tag for messages  
        int myrank = MPI.COMM_WORLD.Rank() ;
        int      p = MPI.COMM_WORLD.Size() ; //this is how to make 8|64 ways parallel
        //int msg_size = 10000000;
        int workers= 8;
        int[][][] token = new int[workers*2][1][1]; //each worker gets 2 matrixes
        int[][][] subtoken = new int[workers*2][1][1]; //each worker gets 2 matrixes
        int msg_size = token.length;

	    int[][][] partitionedX=null;
	    int[][][] partitionedY=null;

        //level = 8th root of p
        int level = 0;
        if (myrank == 0) {
            // Set the token's values if you are process 0
            partitionedX = readFile("a1.txt");
            partitionedY = readFile("b1.txt");
            fillToken(token, partitionedX, partitionedY);
            //parallelSolve(level, token[0], token[1], myrank, workers);

            System.out.println(myrank+"=======\tA and B");
            printMatrix(token[0]);
            printMatrix(token[1]);
            for (int i=1; i<workers; i++){
                MPI.COMM_WORLD.Send(token, i*2, 2, MPI.OBJECT, (myrank + ((p==64)?8*i:i)) , tag);
                if (p==64){
                    fillToken(subtoken, partition(token[0]), partition(token[1]) ); 
                    for (int j=1; j<workers; j++){
                        MPI.COMM_WORLD.Send(subtoken, j*2, 2, MPI.OBJECT, (myrank + j) , tag);
                    }
                }
            }
        } else if (myrank%workers == 0){
             
            MPI.COMM_WORLD.Recv(token, 0, msg_size, MPI.OBJECT, 0, tag);

            fillToken(subtoken, partition(token[0]), partition(token[1]) ); 
            
            System.out.println(myrank+"=======\tA1 and B1");
            printMatrix(subtoken[0]);
            printMatrix(subtoken[1]);
            
            for (int i=1; i<workers; i++){
                MPI.COMM_WORLD.Send(subtoken, i*2, 2, MPI.OBJECT, (myrank + i) , tag);
            }

        } else {
            MPI.COMM_WORLD.Recv(token, 0, msg_size, MPI.OBJECT, myrank-(myrank%8), tag);
            if(sleep){Thread.sleep(1000*myrank);}
            System.out.println(myrank+"=======\tA11 and B11");
            printMatrix(token[0]);
            printMatrix(token[1]);
        }
        //TODO: Delete
        if(p==64 && myrank%workers==0){ 
            int[][][] savedToken=token;
            token=subtoken;
        }
        int[][]a = token[0]; int[][]b = token[1];
        if(sleep){Thread.sleep(p*1000);}
        int[][]d = mult(a,b);
        System.out.println(myrank+"=======\tAfter Mult (1)");
        printMatrix(d);

        if (myrank%workers>=(workers/2)){ //adding
            int[][][]D = new int[][][]{d};
            MPI.COMM_WORLD.Send(D, 0, 1, MPI.OBJECT, myrank-(workers/2) , tag);
        } else {
            int[][][]E=new int[1][1][1]; //not sure why [0][0][0]|null doesnt work
            MPI.COMM_WORLD.Recv(E, 0, msg_size, MPI.OBJECT, myrank+(workers/2), tag);
            int[][]e=E[0];
            System.out.println("=========\tAbout to add(1) for "+myrank +" from "+(myrank+(workers/2)));
            int[][] c = add(d,e); 
            if(sleep){Thread.sleep(p*1000);}
            System.out.println(myrank+"=======\tAfter add (1)");
            printMatrix(c);

            if (myrank%workers!=0) {
                int[][][] C = new int[][][]{c};
                MPI.COMM_WORLD.Send(C, 0, 1, MPI.OBJECT, myrank-(myrank%8), tag);
            } else {
                int[][] SK = new int[c.length*2][c.length*2]; //SK is all the sounds C can make, bc we already use C
                int[][][] C = new int[1][1][1];
                concat(SK, c, 0,0); 
                MPI.COMM_WORLD.Recv(C, 0, msg_size, MPI.OBJECT, myrank+1, tag);
                concat(SK, C[0], 0, SK.length/2);
                MPI.COMM_WORLD.Recv(C, 0, msg_size, MPI.OBJECT, myrank+2, tag);
                concat(SK, C[0], SK.length/2, 0);
                MPI.COMM_WORLD.Recv(C, 0, msg_size, MPI.OBJECT, myrank+3, tag);
                concat(SK, C[0], SK.length/2, SK.length/2);
                System.out.println(myrank+"=======\tAfter Concat (1)");
                printMatrix(SK);
                if (myrank!=0){
                    MPI.COMM_WORLD.Send(new int[][][]{SK}, 0, 1, MPI.OBJECT, 0, tag);
                }
                

                if (p!=8){ 
                                
	                if (myrank>=p/2) {
	                    int[][][]SSKK = new int[][][]{SK}; //bc fuck
	                    MPI.COMM_WORLD.Send(SSKK, 0, 1, MPI.OBJECT, myrank-32, tag);
	                } else {
	
	                    int[][][]F=new int[1][1][1]; //not sure why [0][0][0]|null doesnt work
	                    MPI.COMM_WORLD.Recv(F, 0, msg_size, MPI.OBJECT, myrank+32, tag);
	                    int[][]f=F[0];
	                    System.out.println("=========\tAbout to add(2) for "+myrank +" from "+(myrank+32));
	                    int[][]g = add(SK,f); 
	                    if(sleep){Thread.sleep(p*1000);}
	                    System.out.println(myrank+"=======\tAfter add(2)");
	                    printMatrix(g);
	                    
	                    
	                    if (myrank==16) {
	                        int[][][] G = new int[][][]{g};
	                        System.out.println("=======\t"+myrank+" about to send:");
	                        printMatrix(G[0]);
	                        MPI.COMM_WORLD.Send(G, 0, 1, MPI.OBJECT, 0, tag);
	                    } else if(myrank==0) {
	                        if(sleep){Thread.sleep(p/2*1000);}
	                        int[][] FP = new int[g.length*2][g.length*2]; //FP is a different beatboxing sound than SK
	                        int[][][] G = new int[1][1][1];
	                        int[][][] H = new int[1][1][1];
	                        int[][][] K = new int[1][1][1];
	                        concat(FP, g, 0,0); 
	                        MPI.COMM_WORLD.Recv(G, 0, 1, MPI.OBJECT, myrank+(2*8), tag);
	                        if(sleep){Thread.sleep(1000);}
	                        System.out.println("========\t"+myrank+" about to recieve from 8:");
	                        printMatrix(G[0]);
	                        concat(FP, G[0], 0, FP.length/2);
	//                        MPI.COMM_WORLD.Recv(H, 0, msg_size, MPI.OBJECT, myrank+(1*8), tag);
	//                        if(sleep){Thread.sleep(1000);}
	//                        System.out.println("========\t"+myrank+" about to recieve from 16:");
	//                        printMatrix(H[0]);
	//                        concat(FP, H[0], FP.length/2, 0);
	//                        MPI.COMM_WORLD.Recv(K, 0, 8, MPI.OBJECT, myrank+(1*8), tag);
	//                        if(sleep){Thread.sleep(1000);}
	//                        System.out.println("========\t"+myrank+" about to recieve from 24:");
	//                        printMatrix(K[0]);
	//                        concat(FP, K[0], FP.length/2, FP.length/2);
	                        System.out.println(myrank+"=======\tAfter concat(2)");
	                        printMatrix(FP);
	                    }
	                }
                }
            }
        }

        MPI.Finalize();
    } // main

    public static void printMatrix(int[][] matrix){
        int n=matrix.length;
        System.out.print("{");
        for (int i = 0; i < n; i++) {
            System.out.print("{");
            for (int j = 0; j < n; j++) {
                System.out.print(matrix[i][j] + " ");
            }
            System.out.print("},");
        }
        System.out.println("}");
    }

    public static int[][][] readFile(String filename){
            int[][][]x=null;
            try {
                BufferedReader br = new BufferedReader(new FileReader(filename));
                String line = br.readLine();
                String[] lineArr = line.split(" "); 
	            int[][] x1 = new int[lineArr.length/2][lineArr.length/2];
	            int[][] x2 = new int[lineArr.length/2][lineArr.length/2];
	            int[][] x3 = new int[lineArr.length/2][lineArr.length/2];
	            int[][] x4 = new int[lineArr.length/2][lineArr.length/2];
                for (int row=0; row<lineArr.length/2; row++){ //assuming square matricies
                    for (int col=0; col<lineArr.length/2; col++){ //assuming square matricies
                        x1[row][col]=Integer.parseInt(lineArr[col]);
                    }
                    for (int col=lineArr.length/2; col<lineArr.length; col++){ //assuming square matricies
                        x2[row][col-lineArr.length/2]=Integer.parseInt(lineArr[col]);
                    }
                       line = br.readLine();
                       lineArr = line.split(" "); 
                }
                for (int row=lineArr.length/2; row<lineArr.length; row++){ //assuming square matricies
                    for (int col=0; col<lineArr.length/2; col++){ //assuming square matricies
                        x3[row-lineArr.length/2][col]=Integer.parseInt(lineArr[col]);
                    }
                    for (int col=lineArr.length/2; col<lineArr.length; col++){ //assuming square matricies
                        x4[row-lineArr.length/2][col-lineArr.length/2]=Integer.parseInt(lineArr[col]);
                    }
                       line = br.readLine();
                       if (line==null){
                           break;
                       }
                       lineArr = line.split(" "); 
                }
                x= new int[][][] {x1,x2,x3,x4};
                br.close();
            } catch (IOException e){
                System.out.println ("I hate you.");
            }
            return x;
    }
    
    public static int[][][] partition(int[][] A){
        int[][] x1 = new int[A.length/2][A.length/2];
        int[][] x2 = new int[A.length/2][A.length/2];
        int[][] x3 = new int[A.length/2][A.length/2];
        int[][] x4 = new int[A.length/2][A.length/2];
        for (int row=0; row<A.length/2; row++){ //assuming square matricies
            for (int col=0; col<A.length/2; col++){ //assuming square matricies
                x1[row][col]=A[row][col];
            }
            for (int col=A.length/2; col<A.length; col++){ //assuming square matricies
                x2[row][col-A.length/2]=A[row][col];
            }
        }
        for (int row=A.length/2; row<A.length; row++){ //assuming square matricies
            for (int col=0; col<A.length/2; col++){ //assuming square matricies
                x3[row-A.length/2][col]=A[row][col];
            }
            for (int col=A.length/2; col<A.length; col++){ //assuming square matricies
                x4[row-A.length/2][col-A.length/2]=A[row][col];
            }
        }
        int[][][] x= new int[][][] {x1,x2,x3,x4};
        return x;
    }

    public static void fillToken(int[][][] token, int[][][] partitionedX, int[][][] partitionedY){
            token[0] =partitionedX[0];
            token[1] =partitionedY[0];
            token[2] =partitionedX[0];
            token[3] =partitionedY[1];
            token[4] =partitionedX[2];
            token[5] =partitionedY[0];
            token[6] =partitionedX[2];
            token[7] =partitionedY[1];
            token[8] =partitionedX[1];
            token[9] =partitionedY[2];
            token[10]=partitionedX[1];
            token[11]=partitionedY[3];
            token[12]=partitionedX[3];
            token[13]=partitionedY[2];
            token[14]=partitionedX[3];
            token[15]=partitionedY[3];
    }

    public static void parallelSolve(int level, int[][]A, int[][]B, int myrank, int workers){ //recurrsive
        //Should be overwritten everywhere
        int[][][]    token = new int[workers*2][1][1]; //each worker gets 2 matrixes
        int[][][] subtoken = new int[workers*2][1][1]; //each worker gets 2 matrixes
        if (myrank==0){
            fillToken(token, partitionedX