package com.hinkmond;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.*;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class WordleIntelligentGuesser {
    final static int WORD_LENGTH = 5;
    final static boolean DEBUG = false;
    final String[] correctArray = new String[WORD_LENGTH];
    final HashSet<String> presentMap = new HashSet<>();
    final HashSet<String> absentMap = new HashSet<>();
    final HashSet<String>  guessedMap = new HashSet<>();
    final ArrayList<String> wordsList = new ArrayList<>();
    final int[] maxScore = {0};
    Map<String, Integer> scoreMap = null;

    public void solvePuzzle() {
        ChromeOptions chrome_options = new ChromeOptions();
        chrome_options.addArguments("--window-size=860,680", "--window-position=0,0", "--remote-allow-origins=*");
        chrome_options.setBinary("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome");
        WebDriver driver = new ChromeDriver(chrome_options);
        WebElement closeIcon;
        WebElement keyEnter;

        InputStream ioStream = this.getClass()
                .getClassLoader()
                .getResourceAsStream("sgb-words.txt");

        if (ioStream == null) {
            throw new IllegalArgumentException("sgb-words.txt" + " is not found");
        }

        // Create Map of 5-letter words
        BufferedReader br = new BufferedReader(new InputStreamReader(ioStream));
        try {
            String word;
            while ((word = br.readLine()) != null) {
                wordsList.add(word);
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        // Create ChromeDriver window
        driver.manage().window();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

       // Open browser with desired URL
        driver.get("https://www.nytimes.com/games/wordle/index.html");

        // First, click the "Continue" button
        WebElement continueButton = driver
                .findElement(By.xpath("/html/body/div[2]/div/div/button"));
        continueButton.click();

        // Next, click the "Play" button
        WebElement playButton = driver
                .findElement(By.xpath("/html/body/div/div/div/div/div[3]/button[2]"));
        playButton.click();

        // Next, click the close "X" button.
        // Selector for Game instructions close "X" button (not SVG)
        closeIcon = driver.findElement(By.xpath("/html/body/div/div/dialog/div/button"));
        closeIcon.click();

        // Selector for World App: #wordle-app-game
        WebElement rootGameApp = driver.findElement(By.cssSelector("#wordle-app-game"));

        // Selector for Enter key: /html/body/div/div/div[2]/div/div[2]/div[3]/button[1]
        keyEnter = driver
                .findElement
                        (By.xpath("/html/body/div/div/div[2]/div/div[2]/div[3]/button[1]"));

        // First, get focus of keyboard in root game element
        rootGameApp.click();

        String[] firstChoices =
                {"AISLE", "TEARS", "STALE", "ADIOS", "RHYME", "STORE", "THYME"};
                //{"AISLE", "TEARS", "REALS", "STALE", "SLIME", "STARE", "STORE"};
                //{"SLIME"};
        Random random = new Random();
        String nextGuess = firstChoices[random.nextInt(firstChoices.length)];
        String prevWord = null;

        for (int i=0; i<6; i++) {
            if (nextGuess != null) {
                System.out.println((i+1) + ". W.I.G. Algorithm Best Next Guess: " + nextGuess.toUpperCase() +
                        ", score: " + maxScore[0] + ", previous correctArray size: " + getNumCorrect(nextGuess,
                        correctArray));

                // Next after focus is attained, send the next guess as key presses to the keyboard
                keyEnter.sendKeys(nextGuess);
                keyEnter.click();

                waitForTileAnimation(driver);

                nextGuess = getNextGuess(driver, i + 1);
                if (getNumCorrect(prevWord, correctArray) == 5) {
                    System.out.println("CORRECT!");
                    break;
                } else {
                    // Print top five guesses
                    System.out.print("   Top 5 guesses: ");

                    List<Map.Entry<String, Integer>> scoreList = new ArrayList<>(scoreMap.entrySet());
                    scoreList.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
                    List<Map.Entry<String, Integer>> topList = scoreList.stream().limit(5)
                            .toList();

                    int count = 0;
                    for (Map.Entry<String, Integer> entry : topList) {
                        if (count > 0) {
                            System.out.print(", ");
                        }
                        System.out.print(entry.getKey() + ": " + entry.getValue());
                        count++;
                    }
                    System.out.println();
                }
                prevWord = nextGuess;
            }
        }
        // Close the browser
        //driver.close();
    }

    public String getNextGuess(WebDriver driver, int currentRowNum) {
        String rowCounterStr = String.valueOf(currentRowNum);


        // Selector for Row 1: #wordle-app-game > div.Board-module_boardContainer__cKb-C > div > div:nth-child(1)
        // Selector for Row 2: #wordle-app-game > div.Board-module_boardContainer__cKb-C > div > div:nth-child(2)
        //..
        // Selector for Row n: #wordle-app-game > div.Board-module_boardContainer__cKb-C > div > div:nth-child(n)
        WebElement gameRow = driver
                .findElement(
                        By.xpath("/html/body/div/div/div/div/div[1]/div/div["
                                + rowCounterStr + "]"));

        // Row Property for word entered: textContent
        final String gameRowLettersStr = gameRow.getDomProperty("textContent");
        List<Character> gameRowLettersList =
                gameRowLettersStr.chars().mapToObj(e -> (char) e).toList();

        int evalPosition = 0;
        for (int colCounter=1; colCounter<6; colCounter++) {
            if (!gameRowLettersList.isEmpty()) {
                String letter = String.valueOf(gameRowLettersList.get(evalPosition));

                String tileElementSelector =
                        "/html/body/div/div/div/div/div[1]/div/div[" +
                                rowCounterStr + "]/div[" + colCounter + "]/div";

                String evaluation = driver.findElement(By.xpath(tileElementSelector))
                                          .getAttribute("data-state");

                switch (evaluation) {
                    case "absent" -> {
                        long count = gameRowLettersStr.codePoints()
                                                      .filter(ch -> ch == letter.charAt(0))
                                                      .count();
                        if (count < 2) {
                            absentMap.add(letter);
                        }
                    }
                    case "present" -> presentMap.add(letter);
                    case "correct" -> correctArray[evalPosition] = letter;
                    default -> {
                    }
                }
            }
            evalPosition++;
        }

        maxScore[0] = 0;
        scoreMap = new HashMap<>();
        final String[] maxWord = {null};
        final int[] correctScore = {0};
        wordsList.forEach(word -> {
            if ((!guessedMap.contains(word)) && (getNumCorrect(word, correctArray) != 5)) {
                // Check for any absent letters
                int absentScore = 0;
                for (Character letterChar : word.toCharArray()) {
                    String letterString = String.valueOf(letterChar);
                    if (absentMap.contains(letterString)) {
                        absentScore += 34;
                    }
                }

                /////
                // Adjustments

                // Temporary word string to char array
                char[] wordCharArray = word.toCharArray();

                // Number of repeated letters in the new guess
                int repeatedLetters;
                Set<String> lettersHashSet = new LinkedHashSet<>();
                for (char c : wordCharArray) {
                    String wordLetter = String.valueOf(c);
                    lettersHashSet.add(wordLetter);
                }
                repeatedLetters = 5 - lettersHashSet.size();

                // Number of same letters in previous guess as in the new guess
                int sameLetters = 0;
                if (DEBUG) {
                    System.out.println("  word: " + word);
                    System.out.println("  gameRowLettersStr: " + gameRowLettersStr);
                }
                for (int i=0; i<wordCharArray.length; i++) {
                    if (word.substring(i, i+1).equals(gameRowLettersStr.substring(i, i+1))) {
                        sameLetters++;
                    }
                }

                // Score for correct letters
                correctScore[0] = 0;
                int correctPosition = 0;
                for (Character wordChar : word.toCharArray()) {
                    String wordLetter = String.valueOf(wordChar);
                    if (wordLetter.equals(correctArray[correctPosition])) {
                        correctScore[0] += 139;
                    }
                    correctPosition++;
                }

                // Reduce score of new guess by number of repeated letters
                correctScore[0] -= (repeatedLetters * 22);


                // Reduce score of new guess by number of same letters as last time
                if (sameLetters > 0) {
                    correctScore[0] -= sameLetters;
                }

                if (((correctScore[0] - absentScore) > maxScore[0]) && !guessedMap.contains(maxWord[0])) {
                    maxScore[0] = (correctScore[0] - absentScore);
                    maxWord[0] = word;
                }

                /////
                // Calculate score for this new guess

                // Score for present letters in the new guess
                int presentScore = 1;
                for (int i=0; i<wordCharArray.length; i++) {
                    String wordLetter = String.valueOf(wordCharArray[i]);
                    if (presentMap.contains(wordLetter)) {
                        presentScore += 46;
                    }
                    // Reduce score if same letter was tried before and is not the correct letter in this position
                    if ((i == gameRowLettersStr.indexOf(wordLetter)) &&
                            (!wordLetter.equals(correctArray[i]))) {
                        presentScore--;
                    }
                }

                // Reduce score of new guess by number of repeated letters
                presentScore -= (repeatedLetters * 46);

                // Reduce score of new guess by number of same letters as last time
                if (sameLetters > 0) {
                    presentScore -= sameLetters;
                }

                // If new max score, then record this as the best guess
                if (((presentScore - absentScore) > maxScore[0]) && !guessedMap.contains(maxWord[0])) {
                    maxScore[0] = (presentScore - absentScore);
                    maxWord[0] = word;
                }

                int score = (correctScore[0] > presentScore) ?
                        (correctScore[0] - absentScore) : (presentScore - absentScore);
                scoreMap.put(word, score);

                if (DEBUG) {
                    System.out.println("guess: " + word + ", present: " + presentScore + ", absent: " +
                            absentScore + ", score: " + (presentScore - absentScore) + ", correct: " +
                            correctScore[0] + ", repeatedLetters: " + repeatedLetters + ", sameLetter: " +
                            sameLetters);
                }

            }
        });

        guessedMap.add(maxWord[0]);
        return maxWord[0];
    }

    public void waitForTileAnimation(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        WebElement tile;
        String rowCounterStr;
        for (int rowCounter = 1; rowCounter < 7; rowCounter++) {
            rowCounterStr = String.valueOf(rowCounter);

            String colCounterStr;
            for (int colCounter = 1; colCounter < 6; colCounter++) {
                colCounterStr = String.valueOf(colCounter);
                // Row 1:
                //   Tile 1: /html/body/div/div/div[2]/div/div[1]/div/div[1]/div[1]/div
                //   Tile 2: /html/body/div/div/div[2]/div/div[1]/div/div[1]/div[2]/div
                //...
                // Tile n: /html/body/div/div/div[2]/div/div[1]/div/div[ROW]/div[COL]/div
                // #wordle-app-game > div.Board-module_boardContainer__TBHNL > div > div:nth-child(1) > div:nth-child(5) > div
                tile = driver.findElement
                                     (By.xpath("/html/body/div/div/div/div/div[1]/div/div[" +
                                             rowCounterStr + "]/div[" + colCounterStr + "]/div"));
                wait.until(ExpectedConditions.attributeToBe(tile, "data-animation", "idle"));
            }
        }
    }

    public int getNumCorrect(String guessWord, String[] correctArray) {
        int numCorrect = 0;
        for (int i=0; i<correctArray.length; i++) {
            if ((correctArray[i] != null) && (guessWord != null) &&
                    (guessWord.substring(i, i+1).equals(correctArray[i]))) {
                numCorrect++;
            }
        }
        return numCorrect;
    }

    public static void main(String[] args) {
        WordleIntelligentGuesser wordleIntelligentGuesser = new WordleIntelligentGuesser();
        wordleIntelligentGuesser.solvePuzzle();
    }
}