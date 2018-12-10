// Databricks notebook source
// MAGIC %md
// MAGIC # Getting Started With Apache Spark:  Food Health Inspection Data
// MAGIC 
// MAGIC ### Loading The Data
// MAGIC 
// MAGIC This is health inspection data imported from the [Open Durham website](https://opendurham.nc.gov/explore/dataset/food-health-inspection-data/table/) and made available thanks to the city of Durham, North Carolina.
// MAGIC 
// MAGIC Our first step is to load the data as an RDD, which we will call `inspections`.

// COMMAND ----------

val inspections = sc.textFile("/FileStore/tables/food_health_inspection_data-62769.csv")

// COMMAND ----------

// MAGIC %md
// MAGIC Our data set includes a *lot* of columns and is semi-colon delimited.  Let's start by looking at a few records to see how everything looks.

// COMMAND ----------

val miniInspections = inspections.take(5)

// COMMAND ----------

// MAGIC %md
// MAGIC We want to do the following:
// MAGIC 1. Take the 13th, 17th, and 37th columns in the delimited list.  These are inspection date, score, and report type, respectively.
// MAGIC 2. Filter out any non-date inspection dates or any inspection dates earlier than the year 2000.  Some invalid inspection dates include N/A, NO, and the year 0.
// MAGIC 3. Ignore any report types which are neither Food Service nor Mobile Food.  The same inspection agency also covers daycares, schools, and other institutions.
// MAGIC 
// MAGIC To get our logic right, first, we'll try it on the miniInspections list.

// COMMAND ----------

miniInspections.filter(dt => dt.split(";")(12).length == 10 && dt.split(";")(12).substring(0,4).toInt >= 2000 && (dt.split(";")(36) == "Food Service" || dt.split(";"(36)) == "Mobile Food"))

// COMMAND ----------

// MAGIC %md
// MAGIC As we build out more and more rules, the filter function will get tougher to understand.  So let's create some functions that we can use to simplify matters.

// COMMAND ----------

def IsValidLine(line:String) = { line.split(";").length > 37 }
def IsValidDate(date:String) = { date.length == 10 && date.substring(0,4).toInt >= 2000 }
def IsValidInspectionType(inspectionType:String) = { inspectionType == "Food Service" || inspectionType == "Mobile Food" }
def HasRating(rating:String) = { rating.length > 0 }

// COMMAND ----------

// MAGIC %md
// MAGIC Now we can redefine `miniInspections` with these functions and map our input set to our desired output array:  an array of date, year, score, and report type.

// COMMAND ----------

val miniInspectionSet = miniInspections.filter(dt => IsValidDate(dt.split(";")(12)) && IsValidInspectionType(dt.split(";")(36))).
                                        map(x => (x.split(";")(12), x.split(";")(12).substring(0,4).toInt, x.split(";")(16).toInt, x.split(";")(36)))

// COMMAND ----------

// MAGIC %md
// MAGIC Once we have the specifics down and working with our mini inspection set, let's do it for real with the full data set.

// COMMAND ----------

val inspectionSet = inspections.filter(x => IsValidLine(x) && IsValidDate(x.split(";")(12)) && IsValidInspectionType(x.split(";")(36)) && HasRating(x.split(";")(16))).
                                map(x => (x.split(";")(12), x.split(";")(12).substring(0,4).toInt, x.split(";")(16).toFloat, x.split(";")(36)))

// COMMAND ----------

// MAGIC %md
// MAGIC Once we've finished prepping and loading our data, we can start answering questions about it.
// MAGIC 
// MAGIC ### Exercise 1
// MAGIC What is the average score for inspections from the year 2000 onward?

// COMMAND ----------

val scores2000 = inspectionSet.filter(x => x._2 >= 2000).
                               map(x => (x._3, 1.0)).
                               reduce((x, y) => (x._1 + y._1, x._2 + y._2))
val mean2000 = scores2000._1 / scores2000._2
println(f"The mean score for inspections from the year 2000 onwards is:  $mean2000%3.2f")

// COMMAND ----------

// MAGIC %md
// MAGIC ### Exercise 2
// MAGIC What is the average score by year for inspections from 2000 onward?

// COMMAND ----------

val scoresByYear = inspectionSet.filter(x => x._2 >= 2000).
                                 map(x => (x._2, x._3)).
                                 aggregateByKey((0.0,0.0))((acc, value) => (acc._1 + value, acc._2 + 1),(acc1, acc2) => (acc1._1 + acc2._1, acc1._2 + acc2._2))
val sumByYear = scoresByYear.mapValues(x => (x._1/x._2)).
                             sortByKey().
                             collect()
println("Average score by year:")
sumByYear.foreach(println(_))

// COMMAND ----------

// MAGIC %md
// MAGIC ### Exercise 3
// MAGIC How many inspections do we have total? 
// MAGIC We know this already because we calculated it to get the answer to the first question.

// COMMAND ----------

scores2000._2

// COMMAND ----------

// MAGIC %md
// MAGIC ###Exercise 4
// MAGIC How many inspections by year for inspections from 2000 onward?

// COMMAND ----------

val inspectionsByYear = inspectionSet.filter(x => x._2 >= 2000).
                                      map(x => (x._2, 1)).
                                      reduceByKey((acc, value) => (acc + value))
println("Total number of inspections by year:")
inspectionsByYear.sortByKey().
                  collect().
                  foreach(println(_))

// COMMAND ----------

// MAGIC %md
// MAGIC ### Exercise 5
// MAGIC How many inspections by report area do we have from 2000 onward?

// COMMAND ----------

val inspectionsByReportArea = inspectionSet.filter(x => x._2 >= 2000).
                                            map(x => (x._4, 1)).
                                            reduceByKey((acc, value) => (acc + value))
inspectionsByReportArea.sortByKey().
                        collect().
                        foreach(println(_))

// COMMAND ----------

// MAGIC %md
// MAGIC ### Exercise 6
// MAGIC What is the average inspection score for Mobile Food versus Food Service from 2008 onward?

// COMMAND ----------

val foodService2008 = inspectionSet.filter(x => x._2 >= 2008 && x._4 == "Food Service").
                                    map(x => (x._3, 1.0)).
                                    reduce((x, y) => (x._1 + y._1, x._2 + y._2))
val meanfs2008 = foodService2008._1 / foodService2008._2
val mobileFood2008 = inspectionSet.filter(x => x._2 >= 2008 && x._4 == "Mobile Food").
                                   map(x => (x._3, 1.0)).
                                   reduce((x, y) => (x._1 + y._1, x._2 + y._2))
val meanmf2008 = mobileFood2008._1 / mobileFood2008._2
println(f"The mean score for Food Service inspections from the year 2008 onwards is $meanfs2008%3.2f and for Mobile Food inspections is $meanmf2008%3.2f.")


// COMMAND ----------

// MAGIC %md
// MAGIC ## Concluding Thoughts
// MAGIC 
// MAGIC In this notebook, we looked at "classic" Spark techniques for working with data, using functional programming concepts such as `map`, `reduce`, and `aggregate`.  These are great to know and can serve you well when set-based solutions are too complicated to use for the problem at hand.  There is a bit of a learning curve with them, however.

// COMMAND ----------


