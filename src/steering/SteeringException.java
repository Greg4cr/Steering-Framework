/*	SteeringException
	Custom exception for various steering issues.

	Gregory Gay (greg@greggay.com)
	Last Updated: 05/13/2014
		- Initial file creation

 */

package steering;

public class SteeringException extends Exception {

	private static final long serialVersionUID = 1L;

	public SteeringException() {
		// TODO Auto-generated constructor stub
	}

	public SteeringException(String message) {
		super(message);
		// TODO Auto-generated constructor stub
	}

	public SteeringException(Throwable cause) {
		super(cause);
		// TODO Auto-generated constructor stub
	}

	public SteeringException(String message, Throwable cause) {
		super(message, cause);
		// TODO Auto-generated constructor stub
	}

	public SteeringException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
		// TODO Auto-generated constructor stub
	}

}
