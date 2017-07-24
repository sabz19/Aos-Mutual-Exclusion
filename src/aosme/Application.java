package aosme;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import static java.lang.Math.log;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class Application {
    
    public Application(int id, int mean, int requestnumber){
        
        String FileName = "File " + id + ".txt";
        int count = 0;
        try{
            /*
            Create New file for each node
            */
            File file = new File(FileName);
            file.createNewFile();
            
            /*
            Generating critical section request
            */
            while (count < requestnumber){
                
                write(FileName, "REQUEST");
                
                /*
                Waiting until the OS's GRANT to enter critical section
                */
                while(!read(FileName, "GRANT")){
                    
                    //Kernel.GeneratingARequest()
                    
                }    
                //Kernel.csEnter()         
                count++;
                TimeUnit.MILLISECONDS.sleep((long) generatenumber(mean));
            }
            /*
            Delete file after the process finish its request
            */
            file.delete();
            
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    /*
    This is the function in Kernel
    
    public static void GeneratingARequest(){
    
        while(nodesNotDone){
            .......
            if(fileHasInput())
                .....
                if(id == tokenHolder && queue.isEmpty())
                    WriteGrantToFile();
                    break;
                else if
                    ...
                else if
                    ....            
                else
                    ....  
        }
    }
    
    */
    private void write(String FileName, String s){
        
        try{
            
            FileWriter file = new FileWriter(FileName);
            BufferedWriter bw = new BufferedWriter(file);
            bw.write(s);
            bw.flush();
            
        } catch(IOException  e){
            e.printStackTrace();
        }
    }
    
    /*
    Check if the file contain GRANT or not
    return true if exist 
    */
    private boolean read(String FileName, String s){
        
        boolean found = false;
        final Scanner scan = new Scanner(FileName);
        while (scan.hasNextLine()){
            final String line = scan.nextLine();
            if(line.contains(s)){
                found = true;
                break;
            }
        }
        return found;
    }
    
    /*
    Function for random variables with exponential probability distribution
    */
    private double generatenumber(int mean){
        
        Random random = new Random();
        double x = random.nextDouble();
        double lambda = 1.0/mean;
        return Math.log(1-x)/(-lambda);
    }
}