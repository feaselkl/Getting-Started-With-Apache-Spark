# Getting Started with Apache Spark

## Obtaining Data

In order to run these demos, you will need to load two data sets.  First, the City of Durham food health inspection survey data set.  I have saved a version of the data set without headers in the Data folder.  Move that to a location of choice available to Spark and change the `val inspections =` line to point to your data location.

The second demo uses the MovieLens data set published by GroupLens research.  You can obtain the MovieLens data set from <a href="https://grouplens.org/datasets/movielens/20m/">the GroupLens website</a>.  This data set is 190 MB, so I did not include it in the repository.  Unzip the data set in a location where Spark can access the data and change two blocks of code.  The first block is the first line, `val ratings =`.  The second line is further down, `val movies =`