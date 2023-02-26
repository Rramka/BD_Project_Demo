
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.{DoubleType, IntegerType, StringType, StructField, StructType}
import org.apache.spark.sql.functions._

object Demo {


  def main(args: Array[String]): Unit = {

    println("Application Start...")

    // run etl
    etl("2020-09-22")("2022-09-24")

    println("Application End...")

  }

   private def etl(fromdate : String) (todate : String): Unit = {

    val spark = SparkSession.builder()
      .appName("BD_Project_demo")
      .master("local[*]")
      .getOrCreate()
    spark.sparkContext.setLogLevel("ERROR")

    import spark.implicits._

    // get data from sqlite tables to spark dataframe

    val clients_schema = StructType(Array(
      StructField("ID", IntegerType, true),
      StructField("ClientID", IntegerType, true),
      StructField("FirstName", StringType, true),
      StructField("LastName", StringType, true),
      StructField("Age", IntegerType, true),
      StructField("IBAN", StringType, true),
      StructField("validFrom", StringType, true),
      StructField("validTo", StringType, true)))
    val clients_data = spark.read.format(source = "jdbc")
      .option("url", "jdbc:sqlite:SQLITE_DB_Demo")
      .option("dbtable", "Clients")
      .schema(clients_schema)
      .load()

    val currency_schema = StructType(Array(
      StructField("ID", IntegerType, true),
      StructField("ccyfrom", StringType, true),
      StructField("ccyto", StringType, true),
      StructField("rate", DoubleType, true)))
    val currency_data = spark.read.format(source = "jdbc")
      .option("url", "jdbc:sqlite:SQLITE_DB_Demo")
      .option("dbtable", "currency")
      .schema(currency_schema)
      .load()

    val transaction_schema = StructType(Array(
      StructField("ID", IntegerType, true),
      StructField("IBAN", StringType, true),
      StructField("Amount", DoubleType, true),
      StructField("CurrencyID", IntegerType, true),
      StructField("Tran_Date", StringType, true)))
    val transaction_data = spark.read.format(source = "jdbc")
      .option("url", "jdbc:sqlite:SQLITE_DB_Demo")
      .option("dbtable", "Transactions")
      .schema(transaction_schema)
      .load()



    // get dataframe with filter
     println("Clients data")
     clients_data.filter($"validFrom" between(fromdate, todate)).show()
     println("Currency data")
     currency_data.show()
     println("Transactions data")
     transaction_data.filter($"Tran_Date" between(fromdate, todate)).show()


     val countBYIBAN = transaction_data.groupBy("IBAN")
                                       .agg(count("ID").alias("TranCount"))


     val client_transactions_df = transaction_data.as("t")
            .join(currency_data.as("c"), $"t.CurrencyID" === $"c.ID", "inner")
            .join(clients_data.as("cl"), $"t.IBAN" === $"cl.IBAN"
                    and ($"t.Tran_Date" between($"cl.validFrom", $"cl.validTo")), "inner")
            .join(countBYIBAN.as("cnt"), $"cnt.IBAN" === $"t.IBAN", "inner")
        .select(
                $"t.ID".alias("tranID"), $"t.IBAN", $"t.Amount", ($"t.Amount" * $"c.rate").alias("AmountGEL")
              , $"c.ccyfrom".alias("tranccy")
              , date_format($"t.Tran_Date", "yyyyMMdd").as("Tran_Date")
              , $"ClientID", $"cl.FirstName", $"cl.LastName", $"cnt.TranCount"
               )

    // create  tadabase
    spark.sql("CREATE  DATABASE IF NOT EXISTS RESULT")
    // save dataframe as table
    client_transactions_df.write
                          .mode("overwrite")
                          .saveAsTable("RESULT.client_transactions")


     // save data to csv file
    client_transactions_df.groupBy($"IBAN", $"ClientID", $"cl.FirstName", $"cl.LastName")
                          .agg(avg("AmountGEL").alias("avgAmountGEL"))
                          .write.option("header", true)
                          .option("delimiter", "|")
                          .mode("overwrite")
                          .csv("Aggregate_transaction_data")

    spark.stop()
  }

}
