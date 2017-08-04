library(raster) # to get map shape file
library(ggplot2) # for plotting and miscellaneuous things
library(ggmap) # for plotting
library(plyr) # for merging datasets
library(scales) # to get nice looking legends
library(maps)

# Get datatset from csv
performance<-read.table("test.csv", header = T, sep = ",")

n <- NROW(performance)

greedy <- performance$Greedy

diameter <- c(0, 0, 0)
node <- c(0, 0, 0)

d1 <- c()
d2 <- c()
d3 <- c()

data <- matrix(c(0), nrow = n, ncol = 3)
runs <- matrix(c(0), nrow = 2, ncol = 3)


linear <- c(0, 0, 0); linear.n <- c(0, 0, 0)
star <- c(0, 0, 0); star.n <- c(0, 0, 0)
binary <- c(0, 0, 0); binary.n <- c(0, 0, 0)

for ( i in 1:n ){
  data[i, 1] = performance$Messages.sent[i]
  data[i, 2] = performance$Average.response.time..milliseconds.[i]
  data[i, 3] = performance$Network.throughput..requests.per.second.[i]
}

for ( i in 1:n ){
  if(length(grep("linear",performance$Configuration[i]))>0){
    diameter[1]= performance$Number.of.nodes[i]-1
    d1 = c(d1, i)
    node[1] = performance$Number.of.nodes[i]
  }
  else if(length(grep("star",performance$Configuration[i]))>0){
    diameter[2] = 2
    d2 = c(d2, i)
    node[2] = performance$Number.of.nodes[i]
  }
  else{
    diameter[3] = 10
    d3 = c(d3, i)
    node[3] = performance$Number.of.nodes[i]
  }
}

for( j in 1:3){
  for( i in 1:n){
    if(i %in% d1){
      if(performance$Greedy[i] == 'true'){
        linear[j] = linear[j] + data[i, j]
        runs[1, 1] = runs[1 ,1] + 1
      }
      else{
        linear.n[j] = linear.n[j] + data[i, j]
        runs[2, 1] = runs[2, 1] + 1
      }
    }
    if(i %in% d2){
      if(performance$Greedy[i] == 'true'){
        star[j] = star[j] + data[i, j]
        runs[1, 2] = runs[1, 2] + 1
      }
      else{
        star.n[j] = star.n[j] + data[i, j]
        runs[2, 2] = runs[2, 2] + 1 
      }
    }
    if(i %in% d3){
      if(performance$Greedy[i] == 'true'){
        binary[j] = binary[j] + data[i, j]
        runs[1, 3] = runs[1, 3] + 1
      }
      else{
        binary.n[j] = binary.n[j] + data[i, j]
        runs[2, 3] = runs[2, 3] + 1
      }
    }
  }
}
runs = runs/3
linear = linear/runs[1, 1]
linear.n = linear.n/runs[2, 1]
star = star/runs[2, 1]
star.n = star.n/runs[2, 2]
binary = binary/runs[1, 3]
binary.n = binary.n/runs[2, 3]

plot(x=diameter, cbind(linear.n[1]/node[1], star.n[1]/node[2], binary.n[1]/node[3]),type="h",col=c("red","green", "blue"),
        xlab="Diameter", ylab = "Message Complexity", lwd=c(10))
legend("topleft", c("linear", "star", "binary"),
       lwd=c(2.5,2.5),col=c("red", "green", "blue"))


plot(diameter, cbind(linear[1]/node[1], star[1]/node[2], binary[1]/node[3]),type="h",col=c("red","green", "blue"),
     xlab="Diameter", ylab = "Message Complexity(greedy)", lwd=c(10))
legend("topleft", c("linear", "star", "binary"),
       lwd=c(2.5,2.5),col=c("red", "green", "blue"))

plot(diameter, cbind(linear.n[2], star.n[2], binary.n[2]),type="h",col=c("red","green", "blue"),
     xlab="Diameter", ylab = "Response Time", lwd=c(10))
legend("topright", c("linear", "star", "binary"),
       lwd=c(2.5,2.5),col=c("red", "green", "blue"))

plot(diameter, cbind(linear[2], star[2], binary[2]),type="h",col=c("red","green", "blue"),
     xlab="Diameter", ylab = "Response Time(greedy)", lwd=c(10))
legend("topright", c("linear", "star", "binary"),
       lwd=c(2.5,2.5),col=c("red", "green", "blue"))


plot(diameter, cbind(linear.n[3], star.n[3], binary.n[3]),type="h",col=c("red","green", "blue"),
     xlab="Diameter", ylab = "Throughput", lwd=c(10))
legend("topleft", c("linear", "star", "binary"),
       lwd=c(2.5,2.5),col=c("red", "green", "blue"))

plot(diameter, cbind(linear[3], star[3], binary[3]),type="h",col=c("red","green", "blue"),
     xlab="Diameter", ylab = "Throughput(greedy)", lwd=c(10))
legend("topleft", c("linear", "star", "binary"),
       lwd=c(2.5,2.5),col=c("red", "green", "blue"))
