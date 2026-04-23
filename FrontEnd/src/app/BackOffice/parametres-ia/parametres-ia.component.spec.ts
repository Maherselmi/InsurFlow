import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ParametresIaComponent } from './parametres-ia.component';

describe('ParametresIaComponent', () => {
  let component: ParametresIaComponent;
  let fixture: ComponentFixture<ParametresIaComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ParametresIaComponent]
    })
    .compileComponents();
    
    fixture = TestBed.createComponent(ParametresIaComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
