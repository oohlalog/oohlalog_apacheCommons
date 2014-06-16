import sys
def main():
	output = ""
	for line in sys.stdin:
		output = output + line
	if "TRACE" in output or "DEBUG" in output:
		if "TRACE" in output:
			print "FAILURE: Trace level logs should not appear when default level is info"
		if "DEBUG" in output:
			print "FAILURE: Debug level logs should not appear when default level is info"
	else:
		print "SUCCESS"


main()