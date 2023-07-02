import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.Set;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/**
 * Main class Compress that uses a finite automata to compress PNG images
 * into text files, and decompress text files into images.
 * 
 * @author sebastienm - 26484765
 * 
 * Javadocs for many of the methods was created and written by ChatGPT. The 
 * code was copied into the prompt section of ChatGPT with an additional line 
 * requesting that javadocs be added to the method. Then the javadocs were
 * coppied to this file and altered appropriately. The code is written entirely 
 * by myself withoout the aid of ChatGPT or any LLM.
 */
public class Compress {
    /** An integer representing the size of the largest word. */
    int largest = 1;
    /** An integer representing the size of the image. */
    int imageSize = 0;
    /** An integer representing the algorithm's next state. */
    int nextState = 0;
    /** An integer representing the algorithm's current state. */
    int currState = 0;
    /** The path where the decompressed image or text file will go. */
    String decOut;
    /** The input filename. */
    String filename;
    /**
     * A list of strings representing the language accepted by the initial state
     * (state 0) of the automata.
     */
    Queue<Integer> acceptedStates = new LinkedList<>();
    /**
     * A list of strings representing the words from input that have an accept state
     * as one of the two states.
     */
    Queue<String> acceptWords = new LinkedList<>();
    /**
     * A list of strings representing the words from input.
     */
    Queue<String> words = new LinkedList<>();
    /**
     * A set of words that have a smaller destination state than initial state
     */
    Set<String> flags = new HashSet<>();
    /**
     * A queue of full paths (words describing pixel adresses) that have been
     * accepted by the
     * automata.
     */
    Set<String> fullPaths = new HashSet<>();
    /**
     * A list of objects of type {@code LanguageOfState}, each representing the
     * language accepted by a particular state.
     */
    LanguageofState langs;
    /** A BufferedImage object representing the image being compressed. */
    BufferedImage im;

    /**
     * 
     * The Compress class reads in a text file or an image, depending on the mode
     * given,
     * and either compresses the file into a .png image, or decompresses a .png
     * image into
     * a text file that represents the automata.
     * 
     * @param fileName the name of the file to be compressed or decompressed
     * @param gui      an integer indicating whether the user has selected to use
     *                 the GUI interface
     * @param mode     an integer indicating whether to compress (0) or decompress
     *                 (1) the given file
     * @param multiRes a string indicating the resolution of the image file, if it
     *                 is a multi-resolution image
     * @throws IOException if there is an error reading in the file
     */
    public Compress(String fileName, int gui, int mode, String multiRes) throws IOException {
        filename = fileName;
        File text = new File(filename);
        if (!(text.exists() && text.isFile())) {
            System.err.println("Input Error - Invalid or missing file");
            System.exit(0);
        }
        int numberStates = 0;
        // will need a multi-res condition
        if (mode == 1) {
            // decompress
            int linenum = 0;
            Scanner scanner = new Scanner(new File(filename));
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                linenum++;
                if (linenum == 1) {
                    String s = line.substring((0)) + "";
                    try {
                        Integer.parseInt(s);
                    } catch (NumberFormatException e) {
                        System.err.println("Decompress Error - Invalid automaton formatting");
                        System.exit(0);
                    }
                    numberStates = Integer.parseInt(s);
                }
                if (linenum == 2) {
                    String[] parts = line.split(" ");
                    for (int k = 0; k < parts.length; k++) {
                        try {
                            Integer.parseInt(parts[k]);
                        } catch (NumberFormatException e) {
                            System.err.println("Decompress Error - Invalid automaton formatting");
                            System.exit(0);
                        }
                        if (Integer.parseInt(parts[k]) >= numberStates) {
                            System.err.println("Decompress Error - Invalid accept state");
                            System.exit(0);
                        }
                        acceptedStates.add(Integer.parseInt(parts[k]));
                    }
                }
                if (linenum > 2) {
                    String[] parts = line.split(" ");
                    if (parts.length != 3) {
                        System.err.println("Decompress Error - Invalid automaton formatting");
                        System.exit(0);
                    }
                    for (int k = 0; k < 3; k++) {
                        try {
                            Integer.parseInt(parts[k]);
                        } catch (NumberFormatException e) {
                            System.err.println("Decompress Error - Invalid automaton formatting");
                            System.exit(0);
                        }
                        if (k == 1 || k == 0) {
                            if (Integer.parseInt(parts[0]) >= numberStates
                                    || Integer.parseInt(parts[1]) >= numberStates) {
                                System.err.println("Decompress Error - Invalid transition");
                                System.exit(0);
                            }
                        } else if (k == 2) {
                            if (!(parts[k].equals("0") || parts[k].equals("1")
                                    || parts[k].equals("2")
                                    || parts[k].equals("3"))) {
                                System.err.println("Decompress Error - Invalid transition");
                                System.exit(0);
                            }
                        }
                    }
                    if (Integer.parseInt(parts[0]) > Integer.parseInt(parts[1]) 
                    && !acceptedStates.contains(Integer.parseInt(parts[1]))) {
                        // flag for reiteration
                        flags.add(parts[1]);
                    }
                    if (acceptedStates.contains(Integer.parseInt(parts[1]))) {
                        acceptWords.add(line);
                    }
                    words.add(line);
                }
            }
            for (String s : words) {
                String[] parts = s.split(" ");
                if (flags.contains(parts[0])) {
                    // add flag to end
                    acceptWords.add(s);
                    flags.add(parts[1]);
                }
            }
            words.addAll(acceptWords);
            decompress();
        } else {
            BufferedImage image = ImageIO.read(new File(filename));
            im = image;
            langs = new LanguageofState();
            if (im.getWidth() != im.getHeight()) {
                System.err.println("Compress Error - Invalid input image");
                System.exit(0);
            }
            compress();
        }
    }

    /**
     * 
     * This method decompresses the input image file by reading the compressed file
     * and constructing the automaton.
     * It adds the words and characters from the compressed file to the language and
     * gets all possible paths.
     * Then it calls the drawImage() method to generate the output image.
     * 
     * @throws IOException if there is an error while reading or writing to files
     */
    public void decompress() throws IOException {
        langs = new LanguageofState();
        for (String s : words) {
            String[] parts = s.split(" ");
            int intiState = Integer.parseInt(parts[0]);
            int destState = Integer.parseInt(parts[1]);
            if (intiState == 0) {
                langs.addWord(destState, parts[2]);
            } else {
                langs.addCharToEnd(intiState, destState, parts[2]);
            }
        }
        getAllPaths();
        decOut = "out" + outString(filename) + "_dec.png";
        drawImage();
    }

    /**
     * 
     * This method compresses the input image file by generating all possible pixel
     * addresses and constructing the automaton.
     * It calls the getAllPixelAdresses() method to get all the possible pixel
     * addresses.
     * Then it constructs the automaton using the constructAutomata() method and
     * writes the compressed file using the writeCompressedFile() method.
     * 
     * @throws IOException if there is an error while reading or writing to files
     */
    public void compress() throws IOException {
        getAllPixelAdresses(im, "");
        System.out.println("here");
        constructAutomata();
        writeCompressedFile(filename);
    }

    /**
     * 
     * This method gets all the possible pixel addresses recursively from the input
     * image and adds them to the fullPaths list.
     * It checks if the input image is completely black or white and stops if it is.
     * 
     * @param im the input image
     * @param w  the current pixel address
     */
    private void getAllPixelAdresses(BufferedImage im, String w) {
        if (isAllBlack(im) == im.getWidth() * im.getWidth()) {
            return;
        }
        if (isAllBlack(im) == 0 && w != "") {
            fullPaths.add(w);
            return;
        }
        for (int j = 0; j < 4; j++) {
            String u = w + j;
            BufferedImage subImage = getSubImageFromInt(im, j);
            getAllPixelAdresses(subImage, u);
        }
    }

    /**
     * 
     * This method checks if the input image is completely black or white.
     * It returns 0 if the image is completely black, 1 if the image is completely
     * white and -1 if it is not completely black or white.
     * 
     * @param im the input image
     * @return 0 if the image is completely black, 1 if the image is completely
     *         white and -1 if it is not completely black or white
     */
    private int isAllBlack(BufferedImage im) {
        int counter = 0;
        int red, green, blue;
        for (int i = 0; i < im.getWidth(); i++) {
            for (int j = 0; j < im.getWidth(); j++) {
                int pixel = im.getRGB(i, j);
                red = (pixel >> 16) & 0xff;
                green = (pixel >> 8) & 0xff;
                blue = pixel & 0xff;
                // counter white pixels
                if (red != 0 || green != 0 || blue != 0) {
                    counter++;
                }
              if (red != 0 && red != 255 || green != 0 && green != 255
                    || blue != 0 && blue != 255) {
                        System.out.println("here");
                    System.err.println("Compress Error - Invalid input image");
                    System.exit(0);
                    }
            }
        }
        if (counter == 0) {
            return 0;
        }
        if (counter == im.getWidth() * im.getWidth()) {
            return 1;
        }
        return -1;
    }

    /**
     * 
     * Returns the sub-image of the given {@code BufferedImage} based on the integer
     * index
     * representing one of the four quadrants of the image.
     * 
     * @param im The BufferedImage to get the sub-image from
     * @param i  The index of the quadrant to extract
     * @return The sub-image corresponding to the given quadrant
     */
    private BufferedImage getSubImageFromInt(BufferedImage im, int i) {
        int quadrantSize = im.getWidth() / 2;
        int x = 0; // x-coordinate of top-left corner of each quadrant
        int y = 0; // y-coordinate of top-left corner of each quadrant
        switch (i) {
            case 0:
                BufferedImage zero = im.getSubimage(x, quadrantSize, quadrantSize, quadrantSize);
                return zero;
            case 1:
                BufferedImage one = im.getSubimage(x, y, quadrantSize, quadrantSize);
                return one;
            case 2:
                BufferedImage two = 
                im.getSubimage(quadrantSize, quadrantSize, quadrantSize, quadrantSize);
                return two;
            case 3:
                BufferedImage three = im.getSubimage(quadrantSize, y, quadrantSize, quadrantSize);
                return three;
            default:
                x += 0;
        }
        return im;
    }

    /**
     * 
     * Constructs a finite automaton representing the set of all strings that can be
     * formed by concatenating the set of
     * all pixel addresses of the input image. Uses a Trie data structure to store
     * and manipulate the language of the
     * automaton. Each node of the Trie corresponds to a state in the automaton. The
     * method generates a compressed version of
     * the input image by writing the transition table of the generated automaton to
     * a file.
     */
    private void constructAutomata() {
        System.out.println(nextState);
        langs.addWordSet(0, fullPaths);
        while (currState <= nextState) {
            Set<String> removedCharLanguage = new HashSet<>();
            for (int a = 0; a < 4; a++) {
                String inty = a + "";
                char c = inty.charAt(0);
                boolean didRemove = langs.hasCharAtFrontOfSomeWord(currState, c);
                if (didRemove) {
                    removedCharLanguage = langs.removeCharFromCurrLang(currState, c);
                }
                boolean stepOne = false;
                if (langs.findLanguage(removedCharLanguage) != -1 && didRemove) {
                    // state exists
                    String w = currState + " " + langs.findLanguage(removedCharLanguage) + " " + c;
                    words.add(w);
                    stepOne = true;
                }
                if (!stepOne) {
                    if (didRemove) {
                        nextState++;
                        langs.addWordSet(nextState, removedCharLanguage);
                        String w = (currState + " " + nextState + " " + c);
                        words.add(w);
                        // state does not exist make a new one
                    }
                }
            }
            currState++;
           
        }
    }

    /**
     * Writes a compressed file of the current state and accepted states to disk.
     * 
     * @param file the name of the file to write to
     * @throws IOException if an I/O error occurs while writing the file
     */
    private void writeCompressedFile(String file) throws IOException {
        String filename = "out" + outString(file) + "_cmp.txt";
        FileWriter compressed = new FileWriter(filename);
        compressed.write(currState + "");
        compressed.write("\n" + acceptedStates.remove());
        for (String s : words) {
            String t = "\n" + s;
            compressed.write(t);
        }
        String s = "\n";
        compressed.write(s);
        compressed.close();
    }

    /**
     * Collects all paths in the language accepted by the current state machine
     * and adds them to a list of full paths.
     */
    public void getAllPaths() {
        for (Integer i : acceptedStates) {
            fullPaths.addAll(langs.stateMap.get(i));
        }
    }

    /**
     * Draws an image representing all full paths in the language accepted
     * by the current state machine and saves it to disk as a PNG file.
     * 
     * @throws IOException if an I/O error occurs while writing the image file
     */
    private void drawImage() throws IOException {
        imageSize = 1 << largest; // calculate image size using bitwise operations
        BufferedImage image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D im = image.createGraphics();
        im.setColor(Color.WHITE);
        im.fillRect(0, 0, 100000, 100000);
        for (String s : fullPaths) {
            drawPixel(im, s.toCharArray(), 0, 0, 0);
        }
        File output = new File(decOut);
        ImageIO.write(image, "png", output);
        System.exit(0);
    }

    /**
     * Recursively draws a pixel for a given word in the image being drawn.
     * 
     * @param im      the graphics object for the image being drawn
     * @param word    the word being drawn as a char array
     * @param x       the x-coordinate of the current pixel being drawn
     * @param y       the y-coordinate of the current pixel being drawn
     * @param counter the current depth of the recursive call
     */
    private void drawPixel(Graphics2D im, char[] word, int x, int y, int counter) {
        int l = largest;
        if (counter == l || word == null || word.length == 0 && !(l == 1)) {
            im.setColor(Color.BLACK);
            int size = 1 << (l - counter);
            im.fillRect(x, y, size, size);
            return;
        }
        int add = 1 << (l - counter - 1);
        char c = word[0];
        char[] newWord = Arrays.copyOfRange(word, 1, word.length);
        switch (c) {
            case '0':
                y += add;
                break;
            case '1':
                break;
            case '2':
                x += add;
                y += add;
                break;
            case '3':
                x += add;
                break;
            default:
                return;
        }
        drawPixel(im, newWord, x, y, counter + 1);
    }

    /**
     * Extracts a substring of the filename parameter that represents the
     * name of the file to be written to disk.
     * 
     * @param filename the full path of the file to extract the name from
     * @return the extracted file name as a string
     */
    private String outString(String filename) {
        int n = 0;
        int m = 0;
        for (int i = filename.length(); i > 0; i--) {
            if (filename.charAt(i - 1) == '.') {
                n = i - 1;
            }
            if (filename.charAt(i - 1) == '/') {
                m = i - 1;
                i = 0;
            }
        }
        String toFile = (filename.substring(m, n));
        return toFile;
    }

    /**
     * 
     * The LanguageofState class represents a finite automaton state, and stores a
     * mapping
     * between state numbers and sets of words that can be recognized by that state.
     * Additionally,
     * it keeps a mapping between sets of words and state numbers for quick
     * retrieval.
     */
    private class LanguageofState {
        /**
         * A mapping between state numbers and sets of words that can be recognized by
         * that state.
         */
        private Map<Integer, Set<String>> stateMap;
        /**
         * A mapping between sets of words and state numbers for quick retrieval.
         */
        private Map<Set<String>, Integer> languages;

        /**
         * Constructs a new LanguageofState object with empty stateMap and languages
         * With a predetermined size.
         */
        private LanguageofState(int size) {
            stateMap = new HashMap<Integer, Set<String>>(size);
            languages = new HashMap<Set<String>, Integer>(size);
        }

         /**
         * Constructs a new LanguageofState object with empty stateMap and languages.
         */
        private LanguageofState() {
            stateMap = new HashMap<Integer, Set<String>>();
            languages = new HashMap<Set<String>, Integer>();
        }

        /**
         * Adds a Set of words to the stateMap with the specified stateNumber.
         * 
         * @param stateNumber the number of the state to which the words belong.
         * @param paths       the Set of words to be added.
         */
        private void addWordSet(int stateNumber, Set<String> paths) {
            Set<String> wordSet = new HashSet<String>(paths);
            stateMap.put(stateNumber, wordSet);
            languages.put(wordSet, stateNumber);
        }

        /**
         * Adds a single word to the stateMap with the specified stateNumber.
         * 
         * @param stateNumber the number of the state to which the word belongs.
         * @param word        the word to be added.
         */
        private void addWord(int stateNumber, String word) {
            Set<String> wordSet = new HashSet<String>();
            wordSet.add(word);
            if (stateMap.containsKey(stateNumber)) {
                stateMap.get(stateNumber).addAll(wordSet);
            } else {
                stateMap.put(stateNumber, wordSet);
            }

        }

        /**
         * Finds the stateNumber associated with a given Set of words.
         * 
         * @param paths the Set of words for which to find the stateNumber.
         * @return the stateNumber associated with the given Set of words, or -1 if not
         *         found.
         */
        private Integer findLanguage(Set<String> paths) {
            if (languages.get(paths) != null) {
                return languages.get(paths);
            } else {
                return -1;
            }
        }

        /**
         * Adds a character to the end of every word in the set of words associated with
         * the
         * initial state, and adds the resulting words to the set of words associated
         * with the
         * destination state.
         * 
         * @param initS the number of the initial state.
         * @param destS the number of the destination state.
         * @param a     the character to be added to the end of each word.
         */
        private void addCharToEnd(int initS, int destS, String a) {
            Set<String> result = new HashSet<String>();
            for (String st : stateMap.get(initS)) {
                st = st + a;
                result.add(st);
                if (st.length() > largest) {
                    largest = st.length();
                }
            }
            if (stateMap.containsKey(destS)) {
                stateMap.get(destS).addAll(result);
            } else {
                stateMap.put(destS, result);
            }

        }

        /**
         * 
         * A private method to remove a specified character from the beginning of each
         * word in the language of the current state.
         * 
         * @param currlang the current state whose language needs to be modified
         * @param a        the character to be removed from the beginning of each word
         * @return a set of words obtained by removing the specified character from the
         *         beginning of each word in the current language
         */
        private Set<String> removeCharFromCurrLang(Integer currlang, char a) {
            Set<String> result = new HashSet<String>();
            for (String s : stateMap.get(currlang)) {
                if (s.charAt(0) == a) {
                    String newS = s.substring(1);
                    result.add(newS);
                }
            }
            return result;
        }

        /**
         * 
         * A private method to check if there exists at least one word in the language
         * of the current state that starts with the specified character.
         * 
         * @param currlang the current state whose language needs to be checked
         * @param a        the character to be searched for at the beginning of each
         *                 word
         * @return true if at least one word in the current language starts with the
         *         specified character, false otherwise
         */
        private boolean hasCharAtFrontOfSomeWord(Integer currlang, char a) {
            if (isempty(stateMap.get(currlang))) {
                acceptedStates.add(currlang);
                return false;
            }
            for (String s : stateMap.get(currlang)) {
                if (s.charAt(0) == a) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 
         * A private method to check if a set of strings is empty or contains only
         * whitespace.
         * 
         * @param s the set of strings to be checked
         * @return true if the set is empty or contains only whitespace, false otherwise
         */
        private boolean isempty(Set<String> s) {
            if (s.size() == 1) {
                for (String t : s) {
                    if (t == "" || t == " ") {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * 
     * main method for Compress
     * 
     * @param args takes in arguments for gui, mode, multi resulution, and the
     *             filename for the
     *             PNG or TXT file
     * @throws IOException when one of the inputs are invalid
     */
    public static void main(String[] args) throws IOException {

        if (args.length != 4) {
            System.err.println("Input Error - Invalid number of arguments");
            System.exit(0);
        }

        String gui = args[0];
        String mode = args[1];
        String multiRes = args[2];
        String path = args[3];

        try {
            Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.println("Input Error - Invalid argument type");
            System.exit(0);
        }
        try {
            Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.err.println("Input Error - Invalid argument type");
            System.exit(0);
        }

        if (!(gui.equals("0") || gui.equals("1"))) {
            System.err.println("Input Error - Invalid GUI argument");
            System.exit(0);
        }

        if (!(mode.equals("1") || mode.equals("2"))) {
            System.err.println("Input Error - Invalid mode");
            System.exit(0);
        }

        if (!(multiRes.equals("f") || multiRes.equals("F")
                || multiRes.equals("t") || multiRes.equals("T"))) {
            System.err.println("Input Error - Invalid multi-resolution flag");
            System.exit(0);
        }
        Stopwatch f = new Stopwatch();
        Compress t = new Compress(path, 0, Integer.parseInt(mode), multiRes);
        System.out.println(f.elapsedTime());

        System.exit(0);
    }
}
