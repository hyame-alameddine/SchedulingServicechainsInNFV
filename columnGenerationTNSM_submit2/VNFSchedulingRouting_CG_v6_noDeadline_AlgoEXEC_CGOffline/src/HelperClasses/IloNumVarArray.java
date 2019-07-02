package HelperClasses;
/**from cutting stock**/

import ilog.concert.IloNumVar;

public class IloNumVarArray {

	int _num           = 0;
	IloNumVar[] _array = new IloNumVar[32];
	
	public void add(IloNumVar ivar) {
	if ( _num >= _array.length ) {
			IloNumVar[] array = new IloNumVar[2 * _array.length];
			System.arraycopy(_array, 0, array, 0, _num);
			_array = array;
		}
		_array[_num++] = ivar;
	}
	
	public void remove(IloNumVar ivar) {
			IloNumVar[] array = new IloNumVar[_array.length - 1];
			int index = 0;
			for(int i=0;i<_array.length;i++){
				if(_array[i] == ivar){
					index = i;
					break;
				}
			}
			for(int i=index;i<_array.length-1;i++){
				_array[i] =_array[i+1];
			}
			_num--;
		}
	
	public IloNumVar getElement(int i) { 
		return _array[i]; 
		}
		public int getSize() { 
			return _num; 
		}
	}

