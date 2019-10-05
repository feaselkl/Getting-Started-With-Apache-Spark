open System
open Microsoft.Spark.Sql

[<EntryPoint>]
let main argv =
    let spark = SparkSession
                    .Builder()
                    .AppName("F# Wait Stats Demo")
                    .GetOrCreate()
    let dataFrame = spark.Read()
                        .Option("inferSchema", true)
                        .Option("header", true)
                        .Csv(argv.[0])

    // Demonstration 1:  using the DataFrame API.
    printfn "Sum of milliseconds waiting where wait is over 10K MS in a time period, grouped by wait type."
    dataFrame
        .Where("MillisecondsWaiting > 10000")
        .GroupBy("WaitType")
        .Sum("MillisecondsWaiting")
        .As("TotalWaitTime")
        .Show()

    // Demonstration 2:  using Spark SQL.
    printfn "Top 20 non-OTHER waits, grouped by wait type and event date."
    dataFrame.CreateOrReplaceTempView("WaitStats")
    spark.Sql("
    SELECT
        WaitType,
        CAST(Time AS DATE) AS EventDate, 
        SUM(MillisecondsWaiting) AS MillisecondsWaiting
    FROM WaitStats
    WHERE
        WaitType <> 'OTHER'
    GROUP BY
        WaitType,
        CAST(Time AS DATE)
    ORDER BY
        MillisecondsWaiting DESC,
        EventDate,
        WaitType
    LIMIT 20").Show(20)

    0 // return an integer exit code
