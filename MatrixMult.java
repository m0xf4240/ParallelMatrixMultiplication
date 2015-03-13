import mpi.*;
import java.io.*;

public class MatrixMult{
	public static final boolean debug=true;
	public static final boolean sleep=true;
	public static int tag;
	public static int tempFileSize = 8; // TODO: move to main, define based on file.txt size and n ways parallel
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
		preprocess("i1.txt","j1.txt",p); //make the tiny files

		parallelSolve(level, partitionedX, partitionedY, myrank, workers, p, msg_size); //this should work or fill with 0

		postprocess("k1.txt",p); //concat the tiny files

		MPI.Finalize();
	} // main

	/**
	 * Perform matrix multiplication starting from distributing the problems.
	 *
	 * @param level level
	 * @param myrank myRank
	 * @param workers 8
	 * @param p number of processes
	 * @param msg_size size used for send and recv calls
	 * @return void
	 */
	public static void parallelSolve(int level, int myrank, int workers, int p, int msg_size) throws MPIException,IOException,InterruptedException{ //recurrsive
		//Should be overwritten everywhere
		int[][][]    token = new int[workers*2][1][1]; //each worker gets 2 matrixes
		int[][][] subtoken = new int[workers*2][1][1]; //each worker gets 2 matrixes
		int[][]          d = new int[1][1];
		//
		// STEP 1: Distribute matrixes
		//

		String[] myFileList = distributeSubProblem(level, myrank, workers, p, msg_size);

		//
		// STEP 2: Multiply
		//
		if (level == 1){
			if(sleep){Thread.sleep(p*1000);}
			String ext = myFileList[0]+".txt"
			d = writeMult("A"+ext,"B"+ext,"C"+ext);
		}

		//
		// STEP 3: Add, by beginning to collect upper halves to lower halves
		//
		add(myFileList,myrank,level);
	}

	/**
	 * Distributes names of files to subproblems / receives names of files.
	 *
	 * @param level level
	 * @param myrank myRank
	 * @param workers 8
	 * @param p number of processes
	 * @param msg_size size used for send and recv calls
	 * @return The files the caller is currently in charge of 
	 */
	public static String[] distributeSubProblems(int level, int myrank, int workers, int p, int msg_size) throws MPIException,IOException,InterruptedException{ 

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
	 * Writes the multiplication of two files to a third.
	 * 
	 * @param	fileA	file name to be multiplied with B
	 * @param	fileB	file name to be multiplied with A
	 * @param	fileO	name of output file to be written
	 * @return	void
	 */
	public static void writeMult(String fileA, String fileB, String fileO) throws IOException{
		BufferedWriter f = new BufferedWriter(new FileWriter(fileO));
		int[] s = readFileRow(fileA,0);
		for (int i=0; i<s.length; i++) {
			String nextLine = "";
			int[] r = readFileRow(fileA,i);
			if (debug) {
				for (int a=0; a<r.length; a++){
					System.out.print(r[a]+",");
				}
				System.out.print(" x ");
			}
			for (int j=0; j<s.length; j++) {
				int[] c = readFileCol(fileB,j);
				if (debug) {
					for (int b=0; b<c.length; b++){
						System.out.print(c[b]+",");
					}
					System.out.println();
				}
				int m = mult(r,c);
				nextLine = nextLine + m + " ";
			}
			if (debug) {
				System.out.println(" = " + nextLine);
			}
			f.write(nextLine);
			f.newLine();
		}
		f.close();
	}

	/**
	 * @param	A	int[] matrix row
	 * @param	B	int[] matrix column
	 * @return	int	result or, if failed, -1
	 */
	public static int mult(int[]A, int[]B) {
		if (A.length != B.length) {
			System.out.println("\t \t ERROR: trying to multiply row of length "+A.length+" with col of length "+B.length);
			return -1;
		}
		int result = 0;
		for (int i=0; i<A.length; i++) {
			result += (A[i] * B[i]);
		}
		return result;
	}

	/**
	 * Sends indicator from upper half to lower half, then calls addFiles.
	 *
	 * @param myFileList List of files the process is currently in charge of 
	 * @param myrank myRank
	 * @param level level
	 * @return void
	 */
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
			addFiles(myFileList,theirFileList,level); //Writes correctly
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

	/**
	 * @param	filesA	String[] file names to be added to filesB
	 * @param	filesB	String[] file names to be added to filesA
	 * @param	filesO	String[] file names to be written to
	 * @return	void
	 */
	public static void addFiles(String[] filesA, String[] filesB, String[] filesO) throws IOException{
		//lists passed in in l-r,t-b order
		// so we add [0] to [0] etc
		for (int i=0; i<filesA.length; i++) {
			BufferedWriter f = new BufferedWriter(new FileWriter(filesO[i]));	//add line by line and write to a new file
			int[] getSize = readFileRow(filesA[0],0);			//TODO: wrap these into a list (also for following methods)
			int size = getSize.length; //assuming square matricies
			for (int j=0; j<size; j++) { //read all rows
				int[] nextRowA = readFileRow(filesA[i],j);
				int[] nextRowB = readFileRow(filesB[i],j);
				f.write(addRows(nextRowA,nextRowB));
				f.newLine();
			}
			f.close();
		}
	}

	/**
	 * @param	A	int[] matricies to be added to B
	 * @param	B	int[] matricies to be added to A
	 * @return	output	String of space separated ints to be written to the output file
	 */
	public static String addRows(int[] A, int[]B) {
		if (A.length != B.length) {
			System.out.println("\t \t ERROR: Method addRows() is trying to add A of length "+A.length+" and B of length "+B.length);
		}
		String output = "" + (A[0] + B[0]);
		for (int i=1; i<A.length; i++) { // stops the extra space problem
			output = output + " " + (A[i] + B[i]);
		}
		return output;
	}

	/**
	 * Finds the process which should be returned to.
	 *
	 * @param rank myRank
	 * @return sender
	 */
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
			System.out.println("Level is "+level); 
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
			System.out.println("Level is "+level); 
			System.out.println("Recving to "+src);
		}
		MPI.COMM_WORLD.Recv(buf, offset, count, MPI.OBJECT, src, tag);
	}

	//-----------------

	/**
	 * Get the names of all the tiny files.
	 * 
	 * @param	p	number of processes
	 * @return	String[] of all tiny files
	 */
	public static String[] getAllFiles(int p){
		return null;
	}
	
	//	this might possibly work better moving some logic so this takes index instead of dest and myrank
	/**
	 * Partitions off a subsection of myFileList to send to dest.
	 * 
	 * @param	myFileList	the files the process is currently in charge of
	 * @param	dest	where we are sending file names
	 * @param	myrank	myRank
	 * @return	String[]
	 */
	public static String[] partitionFileList (String[] myFileList, int dest, int myrank){ 
		return null;
	}
	
	/**
	 *
	 * Returns a square submatrix of given size beginning at given index from a 
	 * larger matrix within some file.
	 * @param file the String filename where the larger matrix is stored
	 * @param myrank currently unused, the rank of the process calling readFile
	 * @param rowNum topmost row index for the subarray
	 * @param colNum leftmost col index for the subarray
	 * @param squareSize size of the desired subarray
	 *
	 **/
	public static int[][] readFile(String file, int myrank, int rowNum, int colNum, int squareSize) throws IOException{
		BufferedReader reader = new BufferedReader(new FileReader(file));
		int[][] subMatrix = new int[squareSize][squareSize];
		String line="";
		int i=0;
		line=reader.readLine();
		while (++i < rowNum){
			line=reader.readLine();
		}
		for (int j=0; j<squareSize; j++){
			int[] subRow = new int[squareSize];
			if (line==null){
				throw new IOException("Reading file "+file+" at line "+(j+i)+" failed.");
			} 
			String[] row = line.split(" ");

			for (int k=colNum; k<colNum+squareSize; k++){
				if (k>=row.length){
					throw new IOException("Reading file "+file+" at line "+(j+i)+" and col "+k+" failed.");
				}
				subRow[k-colNum] = Integer.parseInt(row[k]);
			}
			subMatrix[j]=subRow;
			line=reader.readLine();
		}
		return subMatrix;
	}

	/**
	 * 
	 * Returns a single row of a matrix "file", where "file" may actually be 
	 * broken up into multiple smaller files of the same size.
	 *
	 **/
	public static int[] readFileRow(String file, int rowNum) throws IOException{
		int[] row= new int[tempFileSize];
		BufferedReader reader = new BufferedReader(new FileReader(file));

		String line="";
		for (int j=0; j<=rowNum; j++){
			line = reader.readLine();
		}
		String[] rowPart = line.split(" ");
		for (int j=0; j<rowPart.length; j++){
			row[j]=Integer.parseInt(rowPart[j]);
		}
		return row;
	}

	/**
	 * 
	 * Returns a single col of a matrix "file", where "file" may actually be 
	 * broken up into multiple smaller files of the same size.
	 *
	 **/
	public static int[] readFileCol(String file, int colNum) throws IOException{
		int [] col= new int[tempFileSize];//assume temp files are square.
		BufferedReader reader = new BufferedReader(new FileReader(file));

		String line="";
		int j=0;
		line = reader.readLine();
		while(line!=null){
			String[] row = line.split(" ");
			col[j++]=Integer.parseInt(row[colNum]);
			line = reader.readLine();
		}
		return col;
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

} // class
