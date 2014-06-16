import sys
def main():
	output = ""
	for line in sys.stdin:
		output = output + line
	if output.count('Received: {"insertCount":150,"success":true}') < 8:
		print "FAILURE: Not enough successful posts to OLL"
	elif output.count('Received: {"insertCount":150,"success":true}') > 8:
		print "FAILURE: Too many successful posts to OLL"
	else:
		print "SUCCESS"

main()