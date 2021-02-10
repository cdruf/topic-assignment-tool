
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

public class TextFileReader {

    /**
     * Read a test file line by line. Lines that start with dash or space and blank lines are ignored.
     * 
     * @return List of lines.
     */
    static List<String> readLines(String path) {
        try {
            // check if file exists
            File f = new File(path);
            if (!f.exists()) {
                throw new FileNotFoundException();
            } else if (f.isDirectory()) {
                throw new FileNotFoundException();
            }

            // read file
            ArrayList<String> result = new ArrayList<String>();
            Reader reader = null;
            BufferedReader breader = null;
            reader = new FileReader(path);
            breader = new BufferedReader(reader);
            String line;
            while ((line = breader.readLine()) != null) {
                char c;
                try {
                    c = line.charAt(0);
                    if (c != '-' && c != ' ') { // ignore lines that start with a dash or a space
                        result.add(line.trim());
                    }
                } catch (StringIndexOutOfBoundsException e) {
                    // catch and ignore exception if line is blank
                }
            }
            reader.close();
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            throw new Error();
        }
    }
}
