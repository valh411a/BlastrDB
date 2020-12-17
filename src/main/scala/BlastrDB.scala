package csv

import java.io.File
import java.io.BufferedWriter
import java.io.FileWriter
import net.ruippeixotog.scalascraper.browser.JsoupBrowser
import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import java.io.BufferedReader
import java.io.StringWriter
import java.io.PrintWriter
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Projections._
import org.mongodb.scala.model.Sorts._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

/** BlastrDB
  * pulls data from a formatted website link and parses them into formatted lists.
  *
  * @version 0.19
  * @todo add runtime CLI menu to allow for user interaction
  * @todo add hooks and ouput for mongoDB
  * @todo add scalatest items
  */
object BlastrDB extends App {
  println("\nBlastrDB starting...")

  val loggerFile = new File("debugLog.txt")
  val debugFileBuffer = new BufferedWriter(new FileWriter(loggerFile))
  debugFileBuffer.write("Debug Log start:\n")

  //database hooks
  debugFileBuffer.write("Connecting to MongoDB...\n")
  val mongoClient: MongoClient = MongoClient()
  val database: MongoDatabase = mongoClient.getDatabase("testdb")
  val collection = database.getCollection("name").find()
  //for(i <- collection) {
  //  println(i("Brand").asString.getValue + " " + i("Name").asString.getValue)
  //} //this 'for' loop prints out the db collection as a formatted list of strings
  debugFileBuffer.write("MongoDB successfully connected.\n")

  //CLI
  var userInput = ""
  println(
    "Welcome to BlastrDB, the following options below are avilable to you:\n" +
      "\tpull:\tPulls the data from the list of websites.\n" +
      "\twrite:\tWrites the data to seperate CSV files based on brand.\n" +
      "\texit:\tExits the program."
  )
  while (userInput != "exit") {
    println("Please enter a command: ")
    userInput = scala.io.StdIn.readLine()
    debugFileBuffer.write(s"user input: '$userInput'\n")
    userInput match {
      case "pull" => dataWriteToFile(debugFileBuffer)
      case "write" => writeToBrandFiles(debugFileBuffer)
      case "exit" => println("Exiting program...")
    }
  }

  //TODO: pull data from a 3rd party website

  def writeToBrandFiles(debugFile: BufferedWriter) {
    println("Writing data to seperate CSV files based on brand...")
    val folder = getListOfFiles("./csv-files")
    val bufferFile = new File("compiled-list.csv")
    val bw = new BufferedWriter(new FileWriter(bufferFile))
    //compiled-list.csv is organized by Brand,Name
    for (ftchfile <- folder) {
      val file = io.Source.fromFile(ftchfile)
      for (line <- file.getLines) {
        bw.append(
          (ftchfile
            .getAbsolutePath()
            .substring(
              // this line formats the path to display the file name as the brand
              ftchfile.getAbsolutePath().lastIndexOf("\\") + 1,
              ftchfile.getAbsolutePath().length() - 4
            ) + "," + line + "\n").toString()
        )
      }
    }
    debugFileBuffer.write("Data write to files complete, closing buffer...\n")
    bw.close()
    debugFileBuffer.write("buffer closed.\n")
    println("Data write to files complete.")
  }

  /** getListOfFiles
    * populates a list of files drawn from a specified folder
    *
    * @param dir = the directory to populate the files from
    * @return - List[File] (a list of File objects)
    */
  def getListOfFiles(dir: String): List[File] = {
    val output = new File(dir)
    if (output.exists && output.isDirectory) {
      output.listFiles.filter(_.isFile).toList
    } else {
      List[File]()
    }
  }

  /** pullData
    * pulls blaster data from a webpage based on the specific formatting of the webpage and
    *  formats it into a list in a csv file of the brand name
    *
    * @param link the complete URL of the webpage to pull from
    * @param site the website which the browser scraper will format the pull request from.
    *             See the match/case statement below for different websites to format to.
    * @param output the file path of the csv file to output to
    * @param appending Boolean value for if the new data is to be appended to a file (true),
    *                    or overwrite the current data (false).
    *
    * @example pullData(
    *             "https://example.site",
    *             "Brand",
    *             "folder\\File.txt",
    *             false
    *          )
    */
  def pullData(
      link: String,
      site: String,
      output: String,
      appending: Boolean
  ) = {
    val browser = JsoupBrowser()
    val doc = browser.get(link)
    var scrape = Iterable[String]()
    var bdw: BufferedWriter = null

    val bufferDataFile = new File(output)

    if (appending == false) {
      bdw = new BufferedWriter(new FileWriter(bufferDataFile))
      bdw.write("")
    } else {
      debugFileBuffer.write(
        s"\tAdditional page detected for $output, proceeding to append...\n"
      )
      bdw = new BufferedWriter(new FileWriter(bufferDataFile, true))
    }
    site match {
      case "Nerf Wiki" =>
        scrape = doc >> attrs("title")("a[class=category-page__member-link]")
      case _ =>
        debugFileBuffer.write(
          "Invalid Site Name detected, no list items added to scraper\n"
        )
    }

    for (name <- scrape) {
      bdw.append(name + "\n")
    }
    bdw.close()
  }

  /** getStackTraceString
    * gets the stack trace of a thrown exception and formats it
    * into a string for logging purposes
    *
    * @param t the exception thrown
    */
  def getStackTraceAsString(t: Throwable) = {
    val sw = new StringWriter
    t.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  /** dataWriteToFile
    * pulls the data from the dataPullParse.csv file and writes it into
    * an aggregate csv file titled 'compiled-list.csv' utilizing the 'pullData'function
    *
    * @param fileBuffer the debug file
    */
  def dataWriteToFile(fileBuffer: BufferedWriter) = {
    debugFileBuffer.write("Beginning data pull from websites...\n")
    println("Beginning Data pull from websites...")
    val dataPullSource = io.Source.fromFile("dataPullParse.csv")
    for (line <- dataPullSource.getLines) {
      val cols = line.split(",").map(_.trim)
      debugFileBuffer.write("Pulling data from " + cols(0) + "... \n")
      try {
        pullData(cols(0), cols(1), cols(2), cols(3).toBoolean)
      } catch {
        case e: IllegalArgumentException =>
          println(
            "!!!Incorrect formatting found in dataPullParse.csv, check debug.txt for details!!!"
          )
          debugFileBuffer.write(getStackTraceAsString(e))

      }
    }
    println("Data pull complete.")
    debugFileBuffer.write(
      "Data pull complete, writing data to aggregate csv file \"compiled-list.csv\"...\n"
    )
    dataPullSource.close
  }

  debugFileBuffer.write("debug log complete.")
  debugFileBuffer.close()
  println(
    "BlastrDB closed, check debugLog.txt for a detailed runtime log."
  )
}
