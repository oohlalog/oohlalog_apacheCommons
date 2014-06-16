import sys
def main():
	output = ""
	for line in sys.stdin:
		output = output + line
	success = True
	x = 299
	lvls = ["INFO:", "WARN:", "ERROR:", "FATAL:"]
	while x >= 0:
		for lvl in lvls:
			if output.count(lvl + str(x)) != 1:
				print "FAILURE: Did not push " + lvl + str(x) + " the correct number of times"
				success = False
			output = output.replace(lvl + str(x), "")
		x = x - 1
	if (success):
		print "SUCCESS"
main()