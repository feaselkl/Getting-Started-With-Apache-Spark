// Databricks notebook source
// MAGIC %md
// MAGIC # Getting Started with Apache Spark:  Spark SQL
// MAGIC 
// MAGIC For this set of demos, we want to read in the MovieLens 20m data set, which includes approximately 20 million ratings of approximately 27,000 films.  This isn't the largest data set ever, but it does give us a chance to showcase some of Spark's functionality.
// MAGIC 
// MAGIC Note that if you are loading this data locally, you would run something like the following:
// MAGIC 
// MAGIC `val ratings = spark.read.format("CSV").option("header","true").load("hdfs:///user/kevin/Movies/ml-20m/ratings.csv")`
// MAGIC 
// MAGIC Because we are using a Databricks cluster, we can simply reference the already-created tables.

// COMMAND ----------

import org.apache.spark.sql.functions._
val ratings = spark.sql("select * from ratings")

// COMMAND ----------

// MAGIC %md
// MAGIC ### Exercise 1
// MAGIC Ensure that the MovieLens data set really does have 20 million ratings.

// COMMAND ----------

ratings.count()

// COMMAND ----------

// MAGIC %md
// MAGIC ### Exercise 2
// MAGIC Find the number of votes broken down by rating.

// COMMAND ----------

val results = ratings.groupBy("rating").count()
results.orderBy("rating").show(10)

// COMMAND ----------

// MAGIC %md
// MAGIC Note that we use the `show()` function to display results.  We can also use this function to limit the number of results displayed, but this would not replace use of the `LIMIT` operator in Spark SQL to limit the number of rows coming back into a DataFrame.

// COMMAND ----------

// MAGIC %md
// MAGIC ### Exercise 3
// MAGIC Find the movies with the largest number of ratings.

// COMMAND ----------

val ratingsPerMovie = ratings.groupBy("movieId").count()
ratingsPerMovie.orderBy(desc("count")).show(10)

// COMMAND ----------

// MAGIC %md
// MAGIC ### Exercise 3, take 2
// MAGIC But we probably want to know about the specific movies.  So let's read in the movie data set and try again.
// MAGIC 
// MAGIC Note that if you are running this outside of Databricks UAP, you might need to load the movies data set from disk using a command like:
// MAGIC `val movies = spark.read.format("CSV").option("header","true").load("hdfs:///user/kevin/Movies/ml-20m/movies.csv")`

// COMMAND ----------

val movies = spark.sql("select * from movies")
ratingsPerMovie.as("ratingsPerMovie").
                join(movies.as("movies"), col("ratingsPerMovie.movieId") === col("movies.movieId"), "inner").
                select($"title", $"count").
                orderBy(desc("count")).
                show(10, false)
         

// COMMAND ----------

// MAGIC %md
// MAGIC One extra bit of interesting syntax is the optional parameter on the `show()` function.  If the second parameter is set to `true` (the default), Spark will automaticaly truncate strings.  If you set the value to `false`, then Spark will display the entire string as you'd expect to see it.
// MAGIC 
// MAGIC ### Exercise 3, take 3
// MAGIC Now let's do this as a SQL statement.  We'll create temporary Spark views from the DataFrames above and then write a SQL query to get the same results.

// COMMAND ----------

ratingsPerMovie.createOrReplaceTempView("RatingsPerMovie")
movies.createOrReplaceTempView("Movie")
spark.sql("SELECT m.title, rpm.count FROM RatingsPerMovie rpm INNER JOIN Movie m ON rpm.movieId = m.movieId ORDER BY rpm.count DESC LIMIT 10").show(10, false)

// COMMAND ----------

// MAGIC %md
// MAGIC ### Exercise 4
// MAGIC Now we want to look at the top-rated movies, but only those with a minimum of 1000 reviews.
// MAGIC 
// MAGIC Note that I use three quotation marks to set off a multi-line string.

// COMMAND ----------

ratings.createOrReplaceTempView("MovieRating")
spark.sql("""
	SELECT
		m.title,
		COUNT(*) AS numreviews,
		CAST(SUM(mr.rating) / COUNT(mr.rating) AS DECIMAL(3,2)) AS avgrating
	FROM MovieRating mr
		INNER JOIN Movie m
			ON mr.movieId = m.movieId
	GROUP BY
		m.title
	HAVING
		COUNT(*) > 1000
	ORDER BY
		avgrating DESC
""").show(10, false)

// COMMAND ----------

// MAGIC %md
// MAGIC ### Exercise 5
// MAGIC Find the lowest-rated movies with a minimum of 1000 reviews.  These are the movies everyone loves to hate.

// COMMAND ----------

spark.sql("""
	SELECT
		m.title,
		COUNT(*) AS numreviews,
		CAST(SUM(mr.rating) / COUNT(mr.rating) AS DECIMAL(3,2)) AS avgrating 
	FROM MovieRating mr
		INNER JOIN Movie m
			ON mr.movieId = m.movieId
	GROUP BY
		m.title
	HAVING
		COUNT(*) > 1000
	ORDER BY
		avgrating ASC
""").show(10, false)

// COMMAND ----------

// MAGIC %md
// MAGIC ### Exercise 6
// MAGIC 
// MAGIC Now that we have seen the best and worst movies, let's look at the best **popular** films of all-time.  The way we'll define this is that the movie must be in the top 100 most rated movies **and** be in the top 100 highest-rated movies (with a minimum of 10 ratings).

// COMMAND ----------

val topRatedMovies = spark.sql("""
	SELECT
		m.movieId,
		m.title,
		rpm.count
	FROM RatingsPerMovie rpm
		INNER JOIN Movie m
			ON rpm.movieId = m.movieId
	ORDER BY
		rpm.count DESC
	LIMIT 100
""")
val highestRatedMovies = spark.sql("""
	SELECT
		m.movieId,
		m.title,
		COUNT(*) AS numreviews,
		CAST(SUM(mr.rating) / COUNT(mr.rating) AS DECIMAL(3,2)) AS avgrating
	FROM MovieRating mr
		INNER JOIN Movie m
			ON mr.movieId = m.movieId
	GROUP BY
		m.movieId,
		m.title
	HAVING
		COUNT(*) >= 10
	ORDER BY
		avgrating DESC
	LIMIT 100
""")
val highlyRatedAndBelovedMovies = topRatedMovies.as("TopRatedMovies").
                                                 join(highestRatedMovies.as("HighestRatedMovies"), col("TopRatedMovies.movieId") === col("HighestRatedMovies.movieId"), "inner")
highlyRatedAndBelovedMovies.select($"TopRatedMovies.title", $"TopRatedMovies.count", $"HighestRatedMovies.avgrating").
                            orderBy(desc("avgrating")).show(100, false)
highlyRatedAndBelovedMovies.count()
   

// COMMAND ----------

// MAGIC %md
// MAGIC This is a case where knowing Scala can help enhance your querying:  you can use SQL where it makes sense and then combine it with Scala to get the results you want.
// MAGIC 
// MAGIC ### Exercise 6, take 2
// MAGIC We can also solve this problem using window functions.
// MAGIC 
// MAGIC When running this, you might see a warning about partitioning.  Because we are using window functions without partitions, the likelihood of expensive shuffling is high.  We don't care about it here because I'm working on a single-node cluster, but this can be significant in real scenarios because there is the possibility that we will shuffle data between executors, and that can be slow.

// COMMAND ----------

spark.sql("""
WITH reviews AS
(
	SELECT
	    m.movieId,
		m.title,
		COUNT(*) AS numreviews,
		CAST(SUM(mr.rating) / COUNT(mr.rating) AS DECIMAL(3,2)) AS avgrating
	FROM MovieRating mr
		INNER JOIN Movie m
			ON mr.movieId = m.movieId
	GROUP BY
	    m.movieId,
		m.title
	HAVING
		COUNT(*) > 10
),
rankings AS
(
	SELECT
		r.title,
		r.numreviews,
		RANK() OVER (ORDER BY r.numreviews DESC) AS reviewrank,
		r.avgrating,
		RANK() OVER (ORDER BY r.avgrating DESC) as avgratingrank
	FROM reviews r
)
SELECT
	r.title,
	r.numreviews,
	r.reviewrank,
	r.avgrating,
	r.avgratingrank
FROM rankings r
WHERE
	r.reviewrank <= 100
	AND r.avgratingrank <= 100
ORDER BY
	avgrating DESC
""").show(100, false)

// COMMAND ----------

// MAGIC %md
// MAGIC As a bonus, it is possible that the result set for take 2 could be a little bit different from take 1.  This is because the RANK function can pull in more than 100 total films in the event of ties, whereas our first approach limited to 100.

// COMMAND ----------

// MAGIC %md
// MAGIC ### Exercise 7
// MAGIC Find the most common movie genres.  The first thing to note is that genres are stored in a pipe-delimited string on the movies table.

// COMMAND ----------

spark.sql("SELECT Title, Genres FROM Movie").show(50, false)

// COMMAND ----------

// MAGIC %md
// MAGIC Use `split()` to turn the delimited string into an array. Note that `|` is a special character we need to offset it with `\\`.  This works for triple-quoted segments, but for single-quoted segments you will need to double up those slashes, so the offset is `\\\\`:

// COMMAND ----------

spark.sql("SELECT Title, split(Genres, '\\\\|') AS Genres FROM Movie").show(50, false)

// COMMAND ----------

// MAGIC %md
// MAGIC Use `explode()` to put each genre on its own line along with the Title.

// COMMAND ----------

spark.sql("SELECT Title, explode(split(Genres, '\\\\|')) AS Genre FROM Movie").show(50, false)

// COMMAND ----------

// MAGIC %md
// MAGIC Now we can solve the exercise question by combining these pieces together into one statement.

// COMMAND ----------

spark.sql("""
	WITH genres AS
	(
		SELECT
			Title, 
			explode(split(Genres, '\\|')) AS Genre
		FROM Movie
	)
	SELECT
		Genre,
		COUNT(*) AS NumberOfOccurrences
	FROM genres
	GROUP BY
		Genre
	ORDER BY
		NumberOfOccurrences DESC
""").show(50, false)

// COMMAND ----------

// MAGIC %md
// MAGIC ## Concluding Thoughts
// MAGIC 
// MAGIC In this notebook, we used a combination of Scala and Spark SQL to query the MovieLens 20 million rating data set.  Combining Spark SQL with procedural Scala lets us slice and dice the data in a way which might be difficult when using just one technique.
// MAGIC 
// MAGIC Note that we can combine the Spark SQL operations in this notebook with the RDD-centric operations in the first notebook, though you may need to convert between an RDD and a DataFrame to allow for this.  Conversion is not free, so try to limit the amount of conversion you do between these two types.

// COMMAND ----------


