import mpi.*;
import java.io.*;

public class MatrixMult{
    public static final boolean debug=true;
    public static final boolean sleep=true;
    public static int tag;
    public static void main(String[] args) throws MPIException,IOException,InterruptedException{
        MPI.Init(args);

        int my_rank; // Rank of process int source;  // Rank of sender
        int dest;    // Rank of receiver 
        tag=50;  // Tag for messages  
        int myrank = MPI.COMM_WORLD.Rank() ;
        int      p = MPI.COMM_WORLD.Size() ; //this is how to make 8|64 ways parallel
        //int msg_size = 10000000;
        int workers= 8;
        int[][][] token = new int[workers*2][1][1]; //each worker gets 2 matrixes
        int[][][] subtoken = new int[workers*2][1][1]; //each worker gets 2 matrixes
        int msg_size = token.length;

        int[][][] partitionedX=null;
        int[][][] partitionedY=null;

        int level = (int) log(8,p);
        if (myrank == 0) {
            // Set the token's values if you are process 0
            partitionedX = readFile("i1.txt");
            partitionedY = readFile("j1.txt");
            fillToken(token, partitionedX, partitionedY);
        }

        token[0] = parallelSolve(level, partitionedX, partitionedY, myrank, workers, p, msg_size); //this should work or fill with 0

        if (myrank == 0){
            printMatrix(token[0]);
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

    //TODO:replace this with tempfile system
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
    
    //TODO:replace this with tempfile system
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

    //TODO:replace this with tempfile system
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

    public static String[] distributeSubProblems(int level, int[][][]A, int[][][]B, int myrank, int workers, int p, int msg_size) throws MPIException,IOException,InterruptedException{ 

        if (myrank==0){
            myFileList = getAllFiles(p);
            for (int i=1; i<workers; i++){
                int dest = (int) ((myrank + Math.pow(8,level-1)*i)) ;
                String[] subFileList = partitionFileList (myFileList, dest, myrank);
                send(subFileList, i*2, 2, myrank, dest, tag, level);
                if (level>1){
                    parallelSolve(--level,A,B,myrank,workers,p,msg_size); 
                }
            }
        } else if (myrank % Math.pow(workers,level-1) == 0 && myrank>7){ //This shouldnt execute for 8 ways parallel
            recv(myFileList, 0, msg_size, getSender(myrank), myrank, tag, level);

            for (int i=1; i<workers; i++){
                int dest = (int) ((myrank + Math.pow(8,level-1)*i)) ;
                String[] subFileList = partitionFileList (myFileList, dest, myrank);
                send(subFileList, i*2, 2, myrank, dest, tag, level);
                if (level>2){
                    parallelSolve(--level,A,B,myrank,workers,p,msg_size); 
                }
            }
        } else {
            int src = myrank-(myrank%8);
            recv(myFileList, 0, msg_size, src, myrank, tag, level);
            if(sleep){Thread.sleep(1000*myrank);}
        }

        return myFileList;
    }


    /**
     * Specify the data to call MPI.Send() The datatype param for MPI.Send() is
     * assumed to be MPI.Object
     *
     * @param buf send buffer array
     * @param offset initial offet in send buffer
     * @param count number of items to send
     * @param src rank of source (debugging)
     * @param dest rank of destination
     * @param tag message tag
     * @param level level of recurssion calling send() (debugging)
     * @return void
     */
    public static void send(int[][][] buf, int offset, int count, int src, int dest, int tag, int level){
        if(debug){ 
            System.out.println("Level is "+level); } 
            System.out.println("Sending to "+dest);
        }
        MPI.COMM_WORLD.Send(buf, offset, count, MPI.OBJECT, dest, tag);
    }

    /**
     * Specify the data to call MPI.Recv() The datatype param for MPI.Recv() is
     * assumed to be MPI.Object
     *
     * @param buf receive buffer array
     * @param offset initial offet in receive buffer
     * @param count number of items in receive buffer
     * @param src rank of source 
     * @param dest rank of destination (debugging)
     * @param tag message tag
     * @param level level of recurssion calling send() (debugging)
     * @return void
     */
    public static void recv(int[][][] buf, int offset, int count, int src, int dest, int tag, int level){
        if(debug){ 
            System.out.println("Level is "+level); } 
            System.out.println("Recving to "+src);
        }
        MPI.COMM_WORLD.Recv(buf, offset, count, MPI.OBJECT, src, tag);
    }

    public static void parallelSolve(int level, int[][][]A, int[][][]B, int myrank, int workers, int p, int msg_size) throws MPIException,IOException,InterruptedException{ //recurrsive
        //Should be overwritten everywhere
        int[][][]    token = new int[workers*2][1][1]; //each worker gets 2 matrixes
        int[][][] subtoken = new int[workers*2][1][1]; //each worker gets 2 matrixes
        int[][]          d = new int[1][1];
        //
        // STEP 1: Distribute matrixes
        //
        
        String[] myFileList = distributeSubProblem(); //???
        
        //
        // STEP 2: Multiply
        //
        if (level == 1){
            int[][]a = readFile("A"+myFileList[0]+".txt");
            int[][]b = readFile("B"+myFileList[0]+".txt");
            if(sleep){Thread.sleep(p*1000);}
            d = mult(a,b);
            if(debug){
                System.out.println(myrank+"=======\tAfter Mult ("+level+")");
                printMatrix(d);
            }
            write(d, myFileList[0]); //write into C
        }

        //
        // STEP 3: Add, by beginning to collect upper halves to lower halves
        //
        add(myFileList,myrank,level);
    }

    public static void add(String[] myFileList, int myrank, int level){
        
        int distance = myrank - getSender(myrank);
        int lowerHalf = Math.pow(8,level)/2 ;
        boolean isFakeZero = myrank % Math.pow(8,level) == 0;
        if (distance >= lowerHalf && !isFakeZero){
            int dest = (int) (myrank - Math.pow(8,level-1)*4);
            send(myFileList, 0, 1, myrank, dest, tag, level);
        } else {
            int src = (int) (myrank + Math.pow(8,level-1)*4);
            recv( theirFileList, 0, msg_size, src, myrank, tag, level);
            add(myFileList,theirFileList,level); //Writes correctly
            if(sleep){Thread.sleep(p*1000);}

            //
            // STEP 4: Concat, by collecting lower halves to "0"
            //         Actually just wait till everyones done
            //
            if (myrank % Math.pow(8,level) !=0) {
                send(myFileList, 0, 1, myrank, getSender(myrank), tag, level); 
            } else {
                int src;
                String[] foo; //do not actually need this data
                src = (int) (myrank+(1 * Math.pow(8,level-1)) );
                recv(foo, 0, msg_size, src, myrank, tag, level);
                src = (int) (myrank+(2 * Math.pow(8,level-1)) );
                recv(foo, 0, msg_size, src, myrank, tag, level);
                src = (int) (myrank+(3 * Math.pow(8,level-1)) );
                recv(foo, 0, msg_size, src, myrank, tag, level);
           }        
        }
    }

    public static int getSender(int rank){ //TODO:Test: see if can condense
            double logBaseEight = log(8,rank);
            int modEight = rank%8;
            int sender = rank-modEight;
            int closestFake=0; //the closest Fake 0, which is the sender

            for (int i=(int)logBaseEight; i>1; i--){
                int term = (int)Math.pow(8,i); //some power of 8
                int backup = closestFake; 

                while (closestFake < rank){
                    closestFake += term;
                    if (closestFake == rank){
                        closestFake = (i==(int)logBaseEight)?term:backup; //if you are on first iteration of this for loop
                        i=0; //breaks the for loop
                        break;
                    }
                    if (closestFake > rank){
                        closestFake -= term;
                        break;
                    }
                }
                if (closestFake == rank){ //TODO: Delete, as This probably never happens
                    closestFake -= term;
                    break;
                }
            }
 
            if ( rank > closestFake && modEight==0){ //base case
                sender = closestFake;
            }
            if ( rank <=64 && rank%8==0){ //base case: rank<64, fixes 0s
                sender =0;
            }
            if ( logBaseEight == (int)logBaseEight ){ //base case, 0 sends to you
                sender = 0;
            }
        return sender;
    }

    /**
     * Performs log_base(num).
     *
     * @param base the desired log base
     * @param num the argument for log
     * @return the answer
     */
    public static double log(int base, int num) {
        return Math.log(num) / Math.log(base);
    }


    //TODO:replace this with tempfile system
    public static int[][] mult(int[][] A, int[][] B) {
        int aRows = A.length;
        int aColumns = A[0].length;
        int bRows = B.length;
        int bColumns = B[0].length;

        if (aColumns != bRows) {
            throw new IllegalArgumentException("A:Rows: " + aColumns + " did not match B:Columns " + bRows + ".");
        }

        int[][] D = new int[aRows][bColumns];//probably ok not initing
//        System.out.println("D===");
//        printMatrix(D);        
//        System.out.println("===D");

        for (int i = 0; i < aRows; i++) { // aRow
            for (int j = 0; j < bColumns; j++) { // bColumn
                for (int k = 0; k < aColumns; k++) { // aColumn
                    D[i][j] += A[i][k] * B[k][j];
                }
            }
        }

        return D;
    }
    
    //TODO:replace this with tempfile system
    public static int[][] add(int[][] A, int[][] B) {
        int aRows = A.length;
        int aColumns = A[0].length;
        int bRows = B.length;
        int bColumns = B[0].length;

        if (aColumns != bColumns || aRows != bRows) {
            System.out.println("Start of Errer");
            printMatrix(A);
            printMatrix(B);
            throw new IllegalArgumentException("A size "+aRows+" did not match B "+bRows+".");
        }

        int[][] C = new int[aRows][aColumns];//probably ok not initing

        for (int i = 0; i < aRows; i++) { // aRow
            for (int j = 0; j < bColumns; j++) { // bColumn
                C[i][j] += A[i][j] + B[i][j];
            }
        }

        return C;
    }

    //TODO:replace this with tempfile system
    /**
     * Copys int[][] part into int[][] master, starting at the top-left corner
     * of master described by r,c
     *
     * @param   master  the larger array
     * @param   part    the smaller sub-array
     * @param   r       a row coordinate in master
     * @param   c       a col coordinate in master
     */
    public static void concat(int[][] master, int[][] part, int r, int c){
        System.out.print("=========\tPut this matrix into master at "+r+","+c+":");
        printMatrix(part);
        for (int i=0; i<part.length; i++){
            for (int j=0; j<part[i].length; j++){
                master[r+i][c+j]=part[i][j];
            }
        }
    }
} // class
