#!/usr/bin/awk -f
function basename(f){
  n = split(f,name, "/");
  split(name[n],base,".");
  return base[1];
}

function basename_without_number(f){
  bwn_name = basename(f)
  sub(/[0-9]/,"",bwn_name);
  return bwn_name;
}

function find_node(f){
  basefile = basename(f);
  idx = match(basefile, "[0-9]", returnArr)
  return returnArr[0]
}

function find_median (arr, totalVal) {
  asort(arr)
  median = int(totalVal / 2);
  return arr[median];
}

function populate_data (arr, delim, col, count){
  arr[delim]["Sum"] += col;
  arr[delim]["SumX2"] += ((col)^2)
  if(arr[delim]["Min"] == 0 || col < arr[delim]["Min"]){
    arr[delim]["Min"] = col;
  }
  if(col > arr[delim]["Max"]){
    arr[delim]["Max"] = col;
  }
  arr[delim]["Count"]++;
  arr[delim]["Value"][count] = col;
}

function populate_median_and_avg(arr, delim, count){
  arr[delim]["Median"] = find_median(arr[delim]["Value"], count);
  arr[delim]["Avg"] = arr[delim]["Sum"] / count;
  arr[delim]["Stdev"] = sqrt(arr[delim]["SumX2"]/count - (arr[delim]["Avg"]^2));
}

function record_row(arr){
  count = arr["count"];
  current = arr["current"];
#  arr["current"] = 0;
    arr[current]["arrPlaceholder"] = ""; 
    populate_data(arr[current], "Internal", $1, count);
    populate_data(arr[current], "FCFS", $2, count);
    arr["count"]++;
}

function finish_out_aggregate(arr){
  count = arr["count"];
  current = arr["current"];

    populate_median_and_avg(arr[current],"Internal",count);
    populate_median_and_avg(arr[current],"FCFS",count);
    arr["totalTime"] += arr[current]["Internal"]["Sum"];
    arr["totalTrans"] += count;
    
    delete arr[current]["Internal"]["Value"];
    arr["current"]++;
    arr["count"] = 0;
}

function print_to_file( file, arr){
  current = arr["current"];

  {printf "It took %.3f seconds to arr %d files.\n",  arr["totalTime"]/1000, arr["totalTrans"] > file; }
  { print "Internal sum,total transactions, min(ms),max(ms),median(ms),avg(ms),stdev, FCFS sum, total transactions, min, max, median, avg, stdev" >> file }
  for(i = 0; i < current; i++){
    { printf "%d,%d,%d,%d,%d,%f,%f",  arr[i]["Internal"]["Sum"], arr[i]["Internal"]["Count"],arr[i]["Internal"]["Min"], arr[i]["Internal"]["Max"] , arr[i]["Internal"]["Median"], arr[i]["Internal"]["Avg"], arr[i]["Internal"]["Stdev"] >> file}
    { printf ",%d,%d,%d,%d,%d,%f,%f\n",  arr[i]["FCFS"]["Sum"], arr[i]["FCFS"]["Count"],arr[i]["FCFS"]["Min"], arr[i]["FCFS"]["Max"] , arr[i]["FCFS"]["Median"], arr[i]["FCFS"]["Avg"], arr[i]["FCFS"]["Stdev"] >> file }
  }
}

BEGIN{
  FS=","

  # takes advantage of the fact that put a $ in from of a variable means it will reference that row.
  column["create"] = 3
  column["append"] = 4
  column["update"] = 5
  column["delete"] = 6

  granual = 1000;
}

{
  if (FILENAME != _oldFilename){
    if(_oldFilename != ""){
      finish_out_aggregate(stats[column[col]][_oldFilename]);
      if (find_node(FILENAME) == 0){
        print "newNodeFound"
      }
    }
    _oldFilename = FILENAME;
    files[FILENAME] = FNR;
    for (col in column){
      stats[column[col]][FILENAME]["count"] = 0;
      stats[column[col]][FILENAME]["current"]  = 0;
    }
  }
  for( col in column){
    if($column[col] != 0){
      record_row(stats[column[col]][FILENAME] );
    }
    if(stats[column[col]][FILENAME]["count"] % granual == 0) {
      finish_out_aggregate(stats[column[col]][FILENAME]);
    }
  }
}

END{
  for(col in column){
    finish_out_aggregate(stats[column[col]][FILENAME]);
    for (f in files){
#      fname = basename_without_number(f);
      fname = basename(f);
      if(stats[column[col]][f]["totalTime"] != 0) {
        print_to_file( fname"-"col".csv", stats[column[col]][f]);
      }
    }
  }  
}
